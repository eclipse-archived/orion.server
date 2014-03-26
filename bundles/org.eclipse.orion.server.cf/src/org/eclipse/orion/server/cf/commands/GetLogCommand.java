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

public class GetLogCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private String appName;
	private String instanceNo;
	private String logName;
	private String baseRequestLocation;

	public GetLogCommand(Target target, String appName, String instanceNo, String logName, String baseRequestLocation) {
		super(target);
		this.commandName = "Get App Log"; //$NON-NLS-1$
		this.appName = appName;
		this.instanceNo = instanceNo;
		this.logName = logName;
		this.baseRequestLocation = baseRequestLocation;
	}

	public ServerStatus _doIt() {
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());

			// Find the app
			GetAppCommand getAppCommand = new GetAppCommand(target, this.appName);
			IStatus getAppStatus = getAppCommand.doIt();
			if (!getAppStatus.isOK())
				return (ServerStatus) getAppStatus;

			String appUrl = getAppCommand.getApp().getAppJSON().getString("url");

			// check if crash log
			String computedInstanceNo = instanceNo;
			if ("Last Crash".equals(instanceNo)) {
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
				if (crashedInstances.length() > 0) {
					JSONObject crashedInstance = crashedInstances.getJSONObject(crashedInstances.length() - 1);
					computedInstanceNo = crashedInstance.getString("instance");
				}
			}

			String instanceLogsAppUrl = appUrl + "/instances/" + computedInstanceNo + "/files/logs";
			if (logName != null) {
				instanceLogsAppUrl += ("/" + logName);
			}

			URI instanceLogsAppURI = targetURI.resolve(instanceLogsAppUrl);

			GetMethod getInstanceLogMethod = new GetMethod(instanceLogsAppURI.toString());
			HttpUtil.configureHttpMethod(getInstanceLogMethod, target);

			ServerStatus getInstanceLogStatus = HttpUtil.executeMethod(getInstanceLogMethod);
			if (!getInstanceLogStatus.isOK()) {
				return getInstanceLogStatus;
			}

			String response = getInstanceLogStatus.getJsonData().optString("response");

			JSONObject jsonResp = new JSONObject();
			Log log = new Log(appName, logName);
			log.setContents(response);
			log.setLocation(new URI(baseRequestLocation));
			jsonResp.put(instanceNo, log.toJSON());

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, jsonResp);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
