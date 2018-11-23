/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.xfer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Implements import into workspace over SFTP.
 */
class SFTPTransfer {

	private IFileStore localRoot;
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	private final ServletResourceHandler<IStatus> statusHandler;
	private final List<String> options;

	SFTPTransfer(HttpServletRequest req, HttpServletResponse resp, ServletResourceHandler<IStatus> statusHandler, List<String> options) {
		this.request = req;
		this.response = resp;
		this.statusHandler = statusHandler;
		this.options = options;
		initLocalPath(req);
	}

	public void doTransfer() throws ServletException {
		try {
			transferWithExceptions();
		} catch (ServletException e) {
			throw e;
		} catch (Exception e) {
			handleException("Internal error during transfer", e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	private void transferWithExceptions() throws ServletException, IOException, URISyntaxException, JSONException {
		String host, remotePath, user, passphrase;
		int port;
		try {
			JSONObject requestInfo = OrionServlet.readJSONRequest(request);
			host = requestInfo.getString(ProtocolConstants.KEY_HOST);
			remotePath = requestInfo.getString(ProtocolConstants.KEY_PATH);
			port = requestInfo.optInt(ProtocolConstants.KEY_PORT, 22);
			user = requestInfo.getString(ProtocolConstants.KEY_USER_NAME);
			passphrase = requestInfo.getString(ProtocolConstants.KEY_PASSPHRASE);
		} catch (Exception e) {
			handleException("Request body is not in the expected format", e, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		File localFile;
		try {
			localFile = localRoot.toLocalFile(EFS.NONE, null);
		} catch (CoreException e) {
			handleException(NLS.bind("Import is not supported at this location: {0}", localRoot.toString()), e, HttpServletResponse.SC_NOT_IMPLEMENTED);
			return;
		}
		SFTPTransferJob job;
		if (TransferServlet.PREFIX_IMPORT.equals(new Path(request.getPathInfo()).segment(0))) {
			job = new SFTPImportJob(TaskJobHandler.getUserId(request), localFile, host, port, new Path(remotePath), user, passphrase, options);
		} else {
			job = new SFTPExportJob(TaskJobHandler.getUserId(request), localFile, host, port, new Path(remotePath), user, passphrase, options);
		}
		job.schedule();
		TaskInfo task = job.getTask();
		JSONObject result = task.toJSON();
		//Not nice that the import service knows the location of the task servlet, but task service doesn't know this either
		URI requestLocation = ServletResourceHandler.getURI(request);
		URI taskLocation = new URI(requestLocation.getScheme(), requestLocation.getAuthority(), "/task/temp/" + task.getId(), null, null); //$NON-NLS-1$
		result.put(ProtocolConstants.KEY_LOCATION, taskLocation);
		response.setHeader(ProtocolConstants.HEADER_LOCATION, ServletResourceHandler.resovleOrionURI(request, taskLocation).toString());
		OrionServlet.writeJSONResponse(request, response, result);
		response.setStatus(HttpServletResponse.SC_ACCEPTED);
	}

	private void handleException(String string, Exception exception, int httpCode) throws ServletException {
		statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, httpCode, string, exception));
	}

	private void initLocalPath(HttpServletRequest req) {
		IPath path = new Path(request.getPathInfo());
		//first segment is "import" or "export"
		localRoot = NewFileServlet.getFileStore(req, path.removeFirstSegments(1).removeFileExtension());
	}
}