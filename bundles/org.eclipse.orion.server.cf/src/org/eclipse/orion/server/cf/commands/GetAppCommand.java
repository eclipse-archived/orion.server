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
import java.net.URLEncoder;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetAppCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String name;
	private App app;

	public GetAppCommand(Target target, String name) {
		super(target);

		String[] bindings = {name};
		this.commandName = NLS.bind("Get application {0}", bindings);

		this.name = name;
	}

	public ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());

			// Find the app
			String appsUrl = target.getSpace().getCFJSON().getJSONObject("entity").getString("apps_url");
			URI appsURI = targetURI.resolve(appsUrl);
			GetMethod getAppsMethod = new GetMethod(appsURI.toString());
			HttpUtil.configureHttpMethod(getAppsMethod, target);
			getAppsMethod.setQueryString("q=name:" + URLEncoder.encode(name, "UTF8") + "&inline-relations-depth=1");

			ServerStatus getStatus = HttpUtil.executeMethod(getAppsMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject apps = getStatus.getJsonData();

			if (!apps.has("resources") || apps.getJSONArray("resources").length() == 0)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Application not found", null);

			// Get more details about the app
			JSONObject appJSON = apps.getJSONArray("resources").getJSONObject(0).getJSONObject("metadata");

			String summaryAppUrl = appJSON.getString("url") + "/summary";
			URI summaryAppURI = targetURI.resolve(summaryAppUrl);

			GetMethod getSummaryMethod = new GetMethod(summaryAppURI.toString());
			HttpUtil.configureHttpMethod(getSummaryMethod, target);

			getStatus = HttpUtil.executeMethod(getSummaryMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject summaryJSON = getStatus.getJsonData();

			this.app = new App();
			this.app.setAppJSON(appJSON);
			this.app.setSummaryJSON(summaryJSON);

			// GetDebugAppCommand getDebugAppCommand = new GetDebugAppCommand(target, app);
			// ServerStatus getDebugStatus = (ServerStatus) getDebugAppCommand.doIt();
			//
			// JSONObject totalAppJSON = this.app.toJSON();
			// if (getDebugStatus.isOK())
			//	totalAppJSON.put("debug", getDebugStatus.getJsonData());

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, this.app.toJSON());
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	public App getApp() {
		return app;
	}
}
