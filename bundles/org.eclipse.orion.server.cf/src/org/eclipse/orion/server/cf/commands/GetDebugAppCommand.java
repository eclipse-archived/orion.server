/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.commands;

import java.net.URI;
import java.net.URL;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetDebugAppCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App app;

	public GetDebugAppCommand(Target target, App app) {
		super(target);
		this.commandName = "Stop Debug App"; //$NON-NLS-1$
		this.app = app;
	}

	public ServerStatus _doIt() {
		try {
			JSONArray routes = app.getSummaryJSON().optJSONArray("routes");
			if (routes == null) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "No routes", null);
			}

			JSONObject route = routes.getJSONObject(0);
			String host = route.getString("host");
			String domain = route.getJSONObject("domain").getString("name");

			String appUrl = "http://" + host + "." + domain + "/launcher/apps/target";
			URI appURI = URIUtil.toURI(new URL(appUrl));

			GetMethod getMethod = new GetMethod(appURI.toString());
			getMethod.addRequestHeader("Authorization", "Basic " + new String(Base64.encodeBase64(("vcap:holydiver").getBytes())));

			ServerStatus getStatus = HttpUtil.executeMethod(getMethod);
			return getStatus;
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
