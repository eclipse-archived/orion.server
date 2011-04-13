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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONObject;

/**
 * Implements import into workspace over SFTP.
 */
class SFTPImport {

	private final ServletResourceHandler<IStatus> statusHandler;
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	static class BasicUserInfo implements UserInfo {

		private final String password;
		private final String passphrase;

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

		public boolean promptPassword(String message) {
			return false;
		}

		public boolean promptPassphrase(String message) {
			return false;
		}

		public boolean promptYesNo(String message) {
			return false;
		}

		public void showMessage(String message) {
			//not needed
		}

	}

	SFTPImport(HttpServletRequest req, HttpServletResponse resp, ServletResourceHandler<IStatus> statusHandler) {
		this.request = req;
		this.response = resp;
		this.statusHandler = statusHandler;
	}

	public void doImport() throws ServletException {
		String fileName = request.getHeader(ProtocolConstants.HEADER_SLUG);
		if (fileName == null) {
			handleException("Transfer request must indicate target filename", null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		String pathInfo = request.getPathInfo();
		IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
		String host, user, passphrase;
		int port;
		try {
			JSONObject requestInfo = OrionServlet.readJSONRequest(request);
			host = requestInfo.getString(ProtocolConstants.KEY_HOST);
			port = requestInfo.optInt(ProtocolConstants.KEY_PORT, 22);
			user = requestInfo.getString(ProtocolConstants.KEY_USER_NAME);
			passphrase = requestInfo.getString(ProtocolConstants.KEY_PASSPHRASE);
		} catch (Exception e) {
			handleException("Request body is not in the expected format", e, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		try {
			doImport(host, port, user, passphrase);
		} catch (JSchException e) {
			handleException("Import failed", e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	private void doImport(String host, int port, String user, String passphrase) throws JSchException {
		JSch jsch = new JSch();
		Session session = jsch.getSession(user, host, port);
		session.setUserInfo(new BasicUserInfo(null, passphrase));
		session.connect();
		ChannelSftp channel = (ChannelSftp) session.openChannel("sftp"); //$NON-NLS-1$
		try {
			channel.connect();
		} finally {
			channel.disconnect();
		}
	}

	private void handleException(String string, Exception exception, int httpCode) throws ServletException {
		statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, httpCode, string, exception));
	}
}