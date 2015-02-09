/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others 
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
import org.apache.commons.httpclient.methods.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartAppCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App app;
	private int timeout;

	public StartAppCommand(Target target, App app, int timeout) {
		super(target);
		this.commandName = "Start App"; //$NON-NLS-1$
		this.app = app;
		this.timeout = timeout;
	}

	public StartAppCommand(Target target, App app) {
		super(target);
		this.commandName = "Start App"; //$NON-NLS-1$
		this.app = app;
		this.timeout = -1;
	}

	public ServerStatus _doIt() {
		/* multi server status */
		GetAppCommand.expire(target, app.getName());
		try {
			URI targetURI = URIUtil.toURI(target.getUrl());

			String appUrl = this.app.getAppJSON().getString("url"); //$NON-NLS-1$
			URI appURI = targetURI.resolve(appUrl);

			PutMethod startMethod = new PutMethod(appURI.toString());
			HttpUtil.configureHttpMethod(startMethod, target.getCloud());
			startMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

			JSONObject startCommand = new JSONObject();
			startCommand.put("console", true); //$NON-NLS-1$
			startCommand.put("state", "STARTED"); //$NON-NLS-1$ //$NON-NLS-2$
			StringRequestEntity requestEntity = new StringRequestEntity(startCommand.toString(), CFProtocolConstants.JSON_CONTENT_TYPE, "UTF-8"); //$NON-NLS-1$
			startMethod.setRequestEntity(requestEntity);

			ServerStatus startStatus = HttpUtil.executeMethod(startMethod);
			if (!startStatus.isOK())
				return startStatus;

			GetAppCommand.expire(target, app.getName());

			if (timeout < 0) {
				/* extract user defined timeout if present */
				ManifestParseTree manifest = app.getManifest();
				ManifestParseTree timeoutNode = manifest.get(CFProtocolConstants.V2_KEY_APPLICATIONS).get(0).getOpt(CFProtocolConstants.V2_KEY_TIMEOUT);
				timeout = (timeoutNode != null) ? Integer.parseInt(timeoutNode.getValue()) : ManifestConstants.DEFAULT_TIMEOUT;
			}

			/* long running task, keep track */
			timeout = Math.min(timeout, ManifestConstants.MAX_TIMEOUT);
			int attemptsLeft = timeout / 2;

			String msg = NLS.bind("An error occurred during application startup", commandName); //$NON-NLS-1$
			ServerStatus checkAppStatus = new ServerStatus(IStatus.WARNING, HttpServletResponse.SC_BAD_REQUEST, msg, null);

			while (attemptsLeft > 0) {

				/* two seconds */
				Thread.sleep(2000);

				// check instances
				String appInstancesUrl = appUrl + "/instances"; //$NON-NLS-1$
				URI appInstancesURI = targetURI.resolve(appInstancesUrl);

				GetMethod getInstancesMethod = new GetMethod(appInstancesURI.toString());
				HttpUtil.configureHttpMethod(getInstancesMethod, target.getCloud());

				checkAppStatus = HttpUtil.executeMethod(getInstancesMethod);
				if (!checkAppStatus.isOK()) {
					--attemptsLeft;
					continue;
				}

				JSONObject appInstancesJSON = checkAppStatus.getJsonData();

				int instancesNo = appInstancesJSON.length();
				int runningInstanceNo = 0;
				int flappingInstanceNo = 0;

				@SuppressWarnings("unchecked")
				Iterator<String> instanceIt = appInstancesJSON.keys();
				while (instanceIt.hasNext()) {
					JSONObject instanceJSON = appInstancesJSON.getJSONObject(instanceIt.next());
					if ("RUNNING".equals(instanceJSON.optString("state"))) //$NON-NLS-1$ //$NON-NLS-2$
						runningInstanceNo++;
					else if ("FLAPPING".equals(instanceJSON.optString("state"))) //$NON-NLS-1$ //$NON-NLS-2$
						flappingInstanceNo++;
				};

				if (runningInstanceNo == instancesNo) {
					break;
				}

				if (flappingInstanceNo > 0) {
					msg = NLS.bind("An error occurred during application startup", commandName); //$NON-NLS-1$
					checkAppStatus = new ServerStatus(IStatus.WARNING, HttpServletResponse.SC_BAD_REQUEST, msg, checkAppStatus.getJsonData(), null);
					break;
				}

				--attemptsLeft;
				if (attemptsLeft == 0) {
					msg = NLS.bind("An error occurred during application startup", commandName); //$NON-NLS-1$
					checkAppStatus = new ServerStatus(IStatus.WARNING, HttpServletResponse.SC_BAD_REQUEST, msg, checkAppStatus.getJsonData(), null);
				}
			}

			return checkAppStatus;
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
