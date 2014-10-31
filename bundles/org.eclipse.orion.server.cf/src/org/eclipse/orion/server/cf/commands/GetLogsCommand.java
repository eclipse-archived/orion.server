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
	private String appName;
	private String baseRequestLocation;

	public GetLogsCommand(Target target, String appName, String baseRequestLocation) {
		super(target);
		this.commandName = "Get App Logs"; //$NON-NLS-1$
		this.appName = appName;
		this.baseRequestLocation = baseRequestLocation;
	}

	public ServerStatus _doIt() {
		try {
			// Find the app
			GetAppCommand getAppCommand = new GetAppCommand(target, this.appName);
			IStatus getAppStatus = getAppCommand.doIt();
			if (!getAppStatus.isOK())
				return (ServerStatus) getAppStatus;

			String appUrl = getAppCommand.getApp().getAppJSON().getString("url");

			JSONObject jsonResp = new JSONObject();
			setRunningInstances(jsonResp, appUrl);
			setCrashedInstance(jsonResp, appUrl);

			//			while (instancesIterator.hasNext()) {
			//				String instance = (String) instancesIterator.next();
			//				prepareJSONResp(jsonResp, instance, appUrl);
			//			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, jsonResp);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	private ServerStatus setRunningInstances(JSONObject jsonResp, String appUrl) {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());
			String runningInstancesUrl = appUrl + "/instances";
			URI runningInstancesURI = targetURI.resolve(runningInstancesUrl);

			GetMethod getRunningInstancesMethod = new GetMethod(runningInstancesURI.toString());
			HttpUtil.configureHttpMethod(getRunningInstancesMethod, target);

			ServerStatus getStatus = HttpUtil.executeMethod(getRunningInstancesMethod);
			if (!getStatus.isOK()) {
				return getStatus;
			}

			JSONObject runningInstances = getStatus.getJsonData();
			for (@SuppressWarnings("unchecked")
			Iterator<String> iterator = runningInstances.keys(); iterator.hasNext();) {
				JSONArray logsJSON = new JSONArray();
				String runningInstanceNo = iterator.next();
				ServerStatus prepareJSONStatus = prepareJSONResp(logsJSON, runningInstanceNo, appUrl);
				if (prepareJSONStatus.isOK())
					jsonResp.put(runningInstanceNo, logsJSON);
			}
		} catch (Exception e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, e);
		}
		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}

	private ServerStatus setCrashedInstance(JSONObject jsonResp, String appUrl) {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());
			String crashedInstancesUrl = appUrl + "/crashes";
			URI crashedInstancesURI = targetURI.resolve(crashedInstancesUrl);

			GetMethod getCrashedInstancesMethod = new GetMethod(crashedInstancesURI.toString());
			HttpUtil.configureHttpMethod(getCrashedInstancesMethod, target);

			ServerStatus getStatus = HttpUtil.executeMethod(getCrashedInstancesMethod);
			if (!getStatus.isOK()) {
				return getStatus;
			}

			String response = getStatus.getJsonData().getString("response");
			JSONArray crashedInstances = new JSONArray(response);
			JSONArray logsJSON = new JSONArray();
			if (crashedInstances.length() > 0) {
				JSONObject crashedInstance = crashedInstances.getJSONObject(crashedInstances.length() - 1);
				prepareJSONResp(logsJSON, crashedInstance.getString("instance"), appUrl);
				jsonResp.put("Last Crash", logsJSON);
			}
		} catch (Exception e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, e);
		}
		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}

	private ServerStatus prepareJSONResp(JSONArray logsJSON, String instance, String appUrl) {
		try {
			String instanceLogsAppUrl = appUrl + "/instances/" + instance + "/files/logs";

			URI targetURI = URIUtil.toURI(target.getUrl());
			URI instanceLogsAppURI = targetURI.resolve(instanceLogsAppUrl);

			GetMethod getInstanceLogsMethod = new GetMethod(instanceLogsAppURI.toString());
			HttpUtil.configureHttpMethod(getInstanceLogsMethod, target);

			ServerStatus getStatus = HttpUtil.executeMethod(getInstanceLogsMethod);
			if (!getStatus.isOK()) {
				return getStatus;
			}
			String response = getStatus.getJsonData().optString("response");

			if (response == null) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Log request: invalid response from the server", getStatus.getJsonData(), null);
			}

			String[] logFiles = response.split("\n");
			URI baseUri = new URI(baseRequestLocation.endsWith("/") ? baseRequestLocation : baseRequestLocation + "/");

			for (int i = 0; i < logFiles.length; i++) {
				String logFileEntry = logFiles[i];
				String[] logFileEntryParts = logFileEntry.split("\\s+");
				if (logFileEntryParts.length != 2) {
					continue;
				}
				Log log = new Log(appName, logFileEntryParts[0]);
				log.setLocation(baseUri.resolve(log.getName()));
				log.setSize(logFileEntryParts[1]);
				logsJSON.put(log.toJSON());
			}
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}
}
