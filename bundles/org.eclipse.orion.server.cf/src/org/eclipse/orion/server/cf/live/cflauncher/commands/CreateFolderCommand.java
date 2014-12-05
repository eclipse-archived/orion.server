/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.server.cf.live.cflauncher.commands;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.PostMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.commands.ICFCommand;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.Base64;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateFolderCommand implements ICFCommand {
	private static final String DAV_PATH = "/dav/"; //$NON-NLS-1$

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String uri;
	private String path;

	private String cfLauncherAuth;

	/**
	 * 
	 * @param target
	 * @param app
	 * @param uri The URI of the app. Hostname only, for example: "myapp.cfapps.io"
	 * @param path The path of the folder to be created, relative to the app store. Should NOT have a leading "/"
	 * @param contents File contents.
	 */
	public CreateFolderCommand(String uri, String path) {
		this.commandName = "Create folder in app";
		this.uri = uri;
		this.path = path;
		try {
			// ISO-8859-1 see http://stackoverflow.com/a/703341/3394770
			byte[] credentials = (CFLauncherConstants.cfLauncherUsername + ":" + CFLauncherConstants.cfLauncherPassword).getBytes("ISO-8859-1");
			String encoded = new String(Base64.encode(credentials), "ISO-8859-1");
			this.cfLauncherAuth = "Basic " + encoded; //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			// Should never happen
			logger.error(e.getMessage(), e);
		}
	}

	private void configureHttpMethod(HttpMethod method) {
		method.addRequestHeader(new Header("Authorization", cfLauncherAuth));
	}

	@Override
	public IStatus doIt() {
		try {
			/* Construct WebDAV request to update the file. */
			String path = CFLauncherConstants.cfLauncherPrefix + DAV_PATH + this.path; // launcher/dav/whatever.txt
			URI fileUpdateURI = new URI("https", null, this.uri, 443, path, null, null);

			PostMethod createFolderMethod = new PostMethod(fileUpdateURI.toString()) {
				@Override
				public String getName() {
					return "MKCOL";
				}
			};

			configureHttpMethod(createFolderMethod);

			ServerStatus status = executeMethod(createFolderMethod);
			if (!status.isOK())
				return status;

			return status;

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	protected ServerStatus executeMethod(HttpMethod method) throws HttpException, IOException, JSONException {
		try {
			int code = CFActivator.getDefault().getHttpClient().executeMethod(method);

			if (code == 204) {
				/* no content response */
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
			}

			JSONObject result = new JSONObject();
			result.put("response", method.getResponseBodyAsString());

			if (code != 200 && code != 201) {
				// TODO parse error from XML and put in description
				return HttpUtil.createErrorStatus(Status.ERROR, Integer.toString(code), method.getStatusText());
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} finally {
			/* ensure connections are released back to the connection manager */
			method.releaseConnection();
		}
	}
}
