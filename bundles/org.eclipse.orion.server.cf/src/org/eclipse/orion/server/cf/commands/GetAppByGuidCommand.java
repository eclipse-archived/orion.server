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

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Cloud;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetAppByGuidCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String appGuid;
	private App app;

	public GetAppByGuidCommand(Cloud cloud, String appGuid) {
		super(cloud);
		this.commandName = "Get application";
		this.appGuid = appGuid;
	}

	public ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(getCloud().getUrl());

			// Get the app
			URI appsURI = targetURI.resolve("/v2/apps/" + appGuid);
			GetMethod getAppsMethod = new GetMethod(appsURI.toString());
			ServerStatus confStatus = HttpUtil.configureHttpMethod(getAppsMethod, getCloud());
			if (!confStatus.isOK())
				return confStatus;

			ServerStatus getStatus = HttpUtil.executeMethod(getAppsMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject app = getStatus.getJsonData();

			//			if (!apps.has("resources") || apps.getJSONArray("resources").length() == 0)
			//				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Application not found", null);

			// Get more details about the app
			JSONObject appJSON = app.getJSONObject("metadata");

			String summaryAppUrl = appJSON.getString("url") + "/summary";
			URI summaryAppURI = targetURI.resolve(summaryAppUrl);

			GetMethod getSummaryMethod = new GetMethod(summaryAppURI.toString());
			confStatus = HttpUtil.configureHttpMethod(getSummaryMethod, getCloud());
			if (!confStatus.isOK())
				return confStatus;

			getStatus = HttpUtil.executeMethod(getSummaryMethod);
			if (!getStatus.isOK())
				return getStatus;

			JSONObject summaryJSON = getStatus.getJsonData();

			this.app = new App();
			this.app.setAppJSON(appJSON);
			this.app.setSummaryJSON(summaryJSON);
			this.app.setGuid(appJSON.getString("guid"));
			this.app.setName(summaryJSON.getString("name"));

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
