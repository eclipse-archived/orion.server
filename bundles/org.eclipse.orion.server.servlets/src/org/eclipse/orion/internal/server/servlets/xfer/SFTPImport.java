/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.xfer;

import com.jcraft.jsch.UserInfo;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Implements import into workspace over SFTP.
 */
class SFTPImport {

	static class BasicUserInfo implements UserInfo {

		private final String passphrase;
		private final String password;

		BasicUserInfo(String password, String passphrase) {
			this.password = password;
			this.passphrase = passphrase;

		}

		public String getPassphrase() {
			return passphrase;
		}

		public String getPassword() {
			return password;
		}

		public boolean promptPassphrase(String message) {
			return true;
		}

		public boolean promptPassword(String message) {
			return true;
		}

		public boolean promptYesNo(String message) {
			//continue connecting to unknown host
			return true;
		}

		public void showMessage(String message) {
			//not needed
		}

	}

	private IFileStore destinationRoot;
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	private final ServletResourceHandler<IStatus> statusHandler;

	SFTPImport(HttpServletRequest req, HttpServletResponse resp, ServletResourceHandler<IStatus> statusHandler) {
		this.request = req;
		this.response = resp;
		this.statusHandler = statusHandler;
		initSourcePath();
	}

	public void doImport() throws ServletException {
		try {
			importWithExceptions();
		} catch (ServletException e) {
			throw e;
		} catch (Exception e) {
			handleException("Internal error during import", e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	private void importWithExceptions() throws ServletException, IOException, URISyntaxException, JSONException {
		String host, sourcePath, user, passphrase;
		int port;
		try {
			JSONObject requestInfo = OrionServlet.readJSONRequest(request);
			host = requestInfo.getString(ProtocolConstants.KEY_HOST);
			sourcePath = requestInfo.getString(ProtocolConstants.KEY_PATH);
			port = requestInfo.optInt(ProtocolConstants.KEY_PORT, 22);
			user = requestInfo.getString(ProtocolConstants.KEY_USER_NAME);
			passphrase = requestInfo.getString(ProtocolConstants.KEY_PASSPHRASE);
		} catch (Exception e) {
			handleException("Request body is not in the expected format", e, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		File destination;
		try {
			destination = destinationRoot.toLocalFile(EFS.NONE, null);
		} catch (CoreException e) {
			handleException(NLS.bind("Import is not supported at this location: {0}", destinationRoot.toString()), e, HttpServletResponse.SC_NOT_IMPLEMENTED);
			return;
		}
		SFTPImportJob job = new SFTPImportJob(destination, host, port, new Path(sourcePath), user, passphrase);
		job.schedule();
		TaskInfo task = job.getTask();
		JSONObject result = task.toJSON();
		//Not nice that the import service knows the location of the task servlet, but task service doesn't know this either
		URI requestLocation = ServletResourceHandler.getURI(request);
		URI taskLocation = new URI(requestLocation.getScheme(), requestLocation.getAuthority(), "/task/id/" + task.getTaskId(), null, null); //$NON-NLS-1$
		result.put(ProtocolConstants.KEY_LOCATION, taskLocation.toString());
		response.setHeader(ProtocolConstants.HEADER_LOCATION, taskLocation.toString());
		OrionServlet.writeJSONResponse(request, response, result);
		response.setStatus(HttpServletResponse.SC_CREATED);
	}

	private void handleException(String string, Exception exception, int httpCode) throws ServletException {
		statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, httpCode, string, exception));
	}

	private void initSourcePath() {
		String pathInfo = request.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		destinationRoot = NewFileServlet.getFileStore(path);
	}
}