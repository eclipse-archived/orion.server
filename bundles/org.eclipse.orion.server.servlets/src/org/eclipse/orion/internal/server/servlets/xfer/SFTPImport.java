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

import com.jcraft.jsch.*;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import java.io.*;
import java.util.Vector;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
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
			return false;
		}

		public boolean promptPassword(String message) {
			return false;
		}

		public boolean promptYesNo(String message) {
			return false;
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
		String fileName = request.getHeader(ProtocolConstants.HEADER_SLUG);
		if (fileName == null) {
			handleException("Transfer request must indicate target filename", null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
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
		try {
			doImport(host, port, new Path(sourcePath), user, passphrase);
		} catch (ServletException e) {
			throw e;
		} catch (Exception e) {
			handleException("Import failed", e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	private void doImport(String host, int port, IPath sourcePath, String user, String passphrase) throws JSchException, ServletException, SftpException, IOException {
		File destination;
		try {
			destination = destinationRoot.toLocalFile(EFS.NONE, null);
		} catch (CoreException e) {
			handleException(NLS.bind("Import is not supported at this location: {0}", destinationRoot.toString()), e, HttpServletResponse.SC_NOT_IMPLEMENTED);
			return;
		}
		JSch jsch = new JSch();
		Session session = jsch.getSession(user, host, port);
		session.setUserInfo(new BasicUserInfo(null, passphrase));
		session.connect();
		ChannelSftp channel = (ChannelSftp) session.openChannel("sftp"); //$NON-NLS-1$
		try {
			channel.connect();
			doImportDirectory(channel, sourcePath, destination);
		} finally {
			channel.disconnect();
		}
	}

	private void doImportDirectory(ChannelSftp channel, IPath sourcePath, File destination) throws SftpException, IOException {
		@SuppressWarnings("unchecked")
		Vector<LsEntry> children = channel.ls(sourcePath.toString());
		for (LsEntry child : children) {
			String childName = child.getFilename();
			if (child.getAttrs().isDir()) {
				doImportDirectory(channel, sourcePath.append(childName), new File(destination, childName));
			} else {
				doImportFile(channel, sourcePath.append(childName), new File(destination, childName));
			}
		}
	}

	private void doImportFile(ChannelSftp channel, IPath sourcePath, File destination) throws IOException, SftpException {
		IOUtilities.pipe(channel.get(sourcePath.toString()), new FileOutputStream(destination), true, true);
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