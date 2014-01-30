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
package org.eclipse.orion.server.cf.commands;

import java.net.URI;
import java.util.Iterator;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.objects.Log;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetLogsCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String applicationName;
	private String logFileName;
	private String baseRequestLocation;

	public GetLogsCommand(Target target, String applicationName, String logFileName, String baseRequestLocation) {
		super(target);
		this.commandName = "Get App Logs"; //$NON-NLS-1$
		this.applicationName = applicationName;
		this.logFileName = logFileName;
		this.baseRequestLocation = baseRequestLocation;
	}

	public ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());

			// Find the app
			String appsUrl = target.getSpace().getCFJSON().getJSONObject("entity").getString("apps_url");
			URI appsURI = targetURI.resolve(appsUrl);
			GetMethod getAppsMethod = new GetMethod(appsURI.toString());
			HttpUtil.configureHttpMethod(getAppsMethod, target);
			getAppsMethod.setQueryString("q=name:" + applicationName + "&inline-relations-depth=1");

			ServerStatus getStatus = HttpUtil.executeMethod(getAppsMethod);
			if (!getStatus.isOK()) {
				return getStatus;
			}
			JSONObject apps = getStatus.getJsonData();
			if (apps.has("error_code")) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, apps.optString("description"), apps, null);
			}

			if (!apps.has("resources") || apps.getJSONArray("resources").length() == 0)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Application does not exist on the server", null);

			// Get more details about the app
			JSONObject app = apps.getJSONArray("resources").getJSONObject(0).getJSONObject("metadata");

			String instancesAppUrl = app.getString("url") + "/instances";
			URI instancesAppURI = targetURI.resolve(instancesAppUrl);

			GetMethod getInstancesMethod = new GetMethod(instancesAppURI.toString());
			HttpUtil.configureHttpMethod(getInstancesMethod, target);

			getStatus = HttpUtil.executeMethod(getInstancesMethod);
			if (!getStatus.isOK()) {
				return getStatus;
			}
			JSONObject instances = getStatus.getJsonData();
			if (instances.has("error_code")) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, instances.optString("description"), instances, null);
			}

			Iterator<?> instancesIterator = instances.keys();
			JSONObject jsonResp = new JSONObject();

			while (instancesIterator.hasNext()) {
				String instance = (String) instancesIterator.next();
				String instanceLogsAppUrl = app.getString("url") + "/instances/" + instance + "/files/logs";
				if (logFileName != null) {
					instanceLogsAppUrl += ("/" + logFileName);
				}
				URI instanceLogsAppURI = targetURI.resolve(instanceLogsAppUrl);

				GetMethod getInstanceLogsMethod = new GetMethod(instanceLogsAppURI.toString());
				HttpUtil.configureHttpMethod(getInstanceLogsMethod, target);

				getStatus = HttpUtil.executeMethod(getInstanceLogsMethod);
				if (!getStatus.isOK()) {
					return getStatus;
				}
				String response = getStatus.getJsonData().optString("response");

				if (response == null) {
					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Log request: invalid response from the server", getStatus.getJsonData(), null);
				}

				if (logFileName == null) {
					JSONArray logs = new JSONArray();
					String[] logFiles = response.split("\n");

					for (int i = 0; i < logFiles.length; i++) {
						String logFileEntry = logFiles[i];
						String[] logFileEntryParts = logFileEntry.split("\\s+");
						if (logFileEntryParts.length != 2) {
							continue;
						}
						Log log = new Log(applicationName, logFileEntryParts[0]);
						URI baseUri = new URI(baseRequestLocation.endsWith("/") ? baseRequestLocation : baseRequestLocation + "/");
						log.setLocation(baseUri.resolve(log.getName()));
						log.setSize(logFileEntryParts[1]);
						logs.put(log.toJSON());
					}

					jsonResp.put(instance, logs);
				} else {
					Log log = new Log(applicationName, logFileName);
					log.setContents(response);
					log.setLocation(new URI(baseRequestLocation));
					jsonResp.put(instance, log.toJSON());
				}
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, jsonResp);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
