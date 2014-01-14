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
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.cf.utils.MagicJSONObject;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetAppCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;

	private Target target;
	private String name;
	private String contentLocation;

	private App app;

	public GetAppCommand(Target target, String name, String contentLocation) {
		this.commandName = "Get App"; //$NON-NLS-1$
		this.target = target;
		this.name = name;
		this.contentLocation = contentLocation;
	}

	public IStatus doIt() {
		IStatus status = validateParams();
		if (!status.isOK())
			return status;

		try {
			URI targetURI = URIUtil.toURI(target.getUrl());

			// Find the app
			String appsUrl = target.getSpace().getCFJSON().getJSONObject("entity").getString("apps_url");
			URI appsURI = targetURI.resolve(appsUrl);
			GetMethod getAppsMethod = new GetMethod(appsURI.toString());
			HttpUtil.configureHttpMethod(getAppsMethod, target);
			getAppsMethod.setQueryString("q=name:" + name + "&inline-relations-depth=1");

			CFActivator.getDefault().getHttpClient().executeMethod(getAppsMethod);
			String response = getAppsMethod.getResponseBodyAsString();
			JSONObject apps = new JSONObject(response);

			if (!apps.has("resources") || apps.getJSONArray("resources").length() == 0)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Application not found", null);

			// Get more details about the app
			JSONObject appJSON = apps.getJSONArray("resources").getJSONObject(0).getJSONObject("metadata");

			String summaryAppUrl = appJSON.getString("url") + "/summary";
			URI summaryAppURI = targetURI.resolve(summaryAppUrl);

			GetMethod getSummaryMethod = new GetMethod(summaryAppURI.toString());
			HttpUtil.configureHttpMethod(getSummaryMethod, target);

			CFActivator.getDefault().getHttpClient().executeMethod(getSummaryMethod);
			response = getSummaryMethod.getResponseBodyAsString();
			JSONObject summaryJSON = new MagicJSONObject(response);

			this.app = new App();
			this.app.setAppJSON(appJSON);
			this.app.setSummaryJSON(summaryJSON);

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, this.app.toJSON());
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new Status(IStatus.ERROR, CFActivator.PI_CF, msg, e);
		}
	}

	private IStatus validateParams() {
		return Status.OK_STATUS;
	}

	public App getApp() {
		return app;
	}
}
