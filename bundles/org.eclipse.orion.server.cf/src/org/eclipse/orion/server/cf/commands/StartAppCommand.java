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
import java.util.Iterator;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.methods.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartAppCommand extends AbstractCFCommand {

	private static final int MAX_ATTEMPTS = 150;
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App app;

	public StartAppCommand(Target target, App app) {
		super(target);
		this.commandName = "Start App"; //$NON-NLS-1$
		this.app = app;
	}

	public ServerStatus _doIt() {
		/* multi server status */
		MultiServerStatus result = new MultiServerStatus();

		try {
			URI targetURI = URIUtil.toURI(target.getUrl());

			String appUrl = this.app.getAppJSON().getString("url");
			URI appURI = targetURI.resolve(appUrl);

			PutMethod startMethod = new PutMethod(appURI.toString());
			HttpUtil.configureHttpMethod(startMethod, target);
			startMethod.setQueryString("inline-relations-depth=1");

			JSONObject startCommand = new JSONObject();
			startCommand.put("console", true);
			startCommand.put("state", "STARTED");
			StringRequestEntity requestEntity = new StringRequestEntity(startCommand.toString(), CFProtocolConstants.JSON_CONTENT_TYPE, "UTF-8");
			startMethod.setRequestEntity(requestEntity);

			ServerStatus startStatus = HttpUtil.executeMethod(startMethod);
			result.add(startStatus);
			if (!result.isOK())
				return result;

			/* long running task, keep track */
			int attemptsLeft = MAX_ATTEMPTS;

			String msg = NLS.bind("Can not start the application", commandName); //$NON-NLS-1$
			ServerStatus getInstancesStatus = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);

			while (attemptsLeft > 0) {
				/* two seconds */
				Thread.sleep(2000);

				// check instances
				String appInstancesUrl = appUrl + "/instances";
				URI appInstancesURI = targetURI.resolve(appInstancesUrl);

				GetMethod getInstancesMethod = new GetMethod(appInstancesURI.toString());
				HttpUtil.configureHttpMethod(getInstancesMethod, target);

				getInstancesStatus = HttpUtil.executeMethod(getInstancesMethod);
				if (!getInstancesStatus.isOK())
					continue;

				JSONObject appInstancesJSON = getInstancesStatus.getJsonData();

				int instancesNo = appInstancesJSON.length();
				int runningInstanceNo = 0;
				int flappingInstanceNo = 0;

				Iterator<String> instanceIt = appInstancesJSON.keys();
				while (instanceIt.hasNext()) {
					JSONObject instanceJSON = appInstancesJSON.getJSONObject(instanceIt.next());
					if ("RUNNING".equals(instanceJSON.optString("state")))
						runningInstanceNo++;
					else if ("FLAPPING".equals(instanceJSON.optString("state")))
						flappingInstanceNo++;
				};

				if (runningInstanceNo == instancesNo)
					break;

				if (flappingInstanceNo > 0)
					break;

				--attemptsLeft;
			}

			result.add(getInstancesStatus);
			return result;
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
