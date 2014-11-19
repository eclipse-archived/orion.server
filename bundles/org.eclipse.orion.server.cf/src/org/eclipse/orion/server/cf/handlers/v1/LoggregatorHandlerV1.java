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
package org.eclipse.orion.server.cf.handlers.v1;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.commands.*;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.loggregator.LoggregatorClient;
import org.eclipse.orion.server.cf.loggregator.LoggregatorListener;
import org.eclipse.orion.server.cf.objects.*;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggregatorHandlerV1 extends AbstractRESTHandler<Log> {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	public LoggregatorHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected Log buildResource(HttpServletRequest request, String pathString) throws CoreException {
		return null;
	}

	@Override
	protected CFJob handleGet(final Log log, HttpServletRequest request, HttpServletResponse response, final String pathString) {
		final JSONObject targetJSON = extractJSONData(IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_TARGET));
		IPath path = new Path(pathString);
		final String appName = path.segment(0);

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					logger.debug(NLS.bind("LoggregatorHandlerV1 starts collecting logs for: {0}", appName));

					ComputeTargetCommand computeTargetCommand = new ComputeTargetCommand(this.userId, targetJSON);
					IStatus result = computeTargetCommand.doIt();
					if (!result.isOK())
						return result;
					Target target = computeTargetCommand.getTarget();

					GetAppCommand getAppCommand = new GetAppCommand(target, appName);
					IStatus getAppStatus = getAppCommand.doIt();
					if (!getAppStatus.isOK())
						return getAppStatus;
					App app = getAppCommand.getApp();

					GetInfoCommand getInfoCommand = new GetInfoCommand(this.userId, target.getCloud());
					ServerStatus getInfoStatus = (ServerStatus) getInfoCommand.doIt();
					if (!getInfoStatus.isOK())
						return getInfoStatus;

					logger.debug(NLS.bind("Cloud info: {0}", getInfoStatus.getJsonData().toString()));
					String loggingEndpoint = getInfoStatus.getJsonData().getString("logging_endpoint");

					JSONObject messages = new JSONObject();
					LoggregatorListener listener = new LoggregatorListener();

					GetLogCommand getLogCommand = new GetLogCommand(target, loggingEndpoint, app.getAppJSON().getString("guid"), listener);
					IStatus getLogStatus = getLogCommand.doIt();

					if (!getLogStatus.isOK()) {
						new LoggregatorClient().start(target, loggingEndpoint + "/dump/?app=" + app.getAppJSON().get("guid"), listener);
					}

					messages.put("Messages", listener.getMessagesJSON());

					return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, messages, null);
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", pathString); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				} catch (NoClassDefFoundError e) {
					String msg = NLS.bind("Failed to handle request for {0}", pathString); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);

					if (e.getMessage().equals("org/eclipse/jetty/websocket/client/WebSocketClient")) {
						status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Not supported", null);
					}

					logger.error(msg, e);
					return status;
				}
			}
		};
	}
}
