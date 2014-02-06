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
package org.eclipse.orion.server.cf.handlers.v1;

import java.net.URI;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.commands.*;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppsHandlerV1 extends AbstractRESTHandler<App> {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	public AppsHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected App buildResource(HttpServletRequest request, String path) throws CoreException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected CFJob handleGet(App app, HttpServletRequest request, HttpServletResponse response, final String path) {
		final String contentLocation = IOUtilities.getQueryParameter(request, ProtocolConstants.KEY_CONTENT_LOCATION);
		final String name = IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_NAME);
		final JSONObject targetJSON = extractJSONData(IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_TARGET));

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					ComputeTargetCommand computeTarget = new ComputeTargetCommand(this.userId, targetJSON);
					IStatus result = computeTarget.doIt();
					if (!result.isOK())
						return result;
					Target target = computeTarget.getTarget();

					if (contentLocation != null || name != null) {
						return new GetAppCommand(target, name).doIt();
					}

					return getApps(target);
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	@Override
	protected CFJob handlePut(App resource, HttpServletRequest request, HttpServletResponse response, final String path) {
		final JSONObject jsonData = extractJSONData(request);

		final String state = jsonData.optString(CFProtocolConstants.KEY_STATE);
		final String appName = jsonData.optString(CFProtocolConstants.KEY_NAME);
		final JSONObject targetJSON = jsonData.optJSONObject(CFProtocolConstants.KEY_TARGET);
		final String contentLocation = ServletResourceHandler.toOrionLocation(request, jsonData.optString(CFProtocolConstants.KEY_CONTENT_LOCATION));

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					ComputeTargetCommand computeTarget = new ComputeTargetCommand(this.userId, targetJSON);
					IStatus status = computeTarget.doIt();
					if (!status.isOK())
						return status;
					Target target = computeTarget.getTarget();

					GetAppCommand getAppCommand = new GetAppCommand(target, appName);
					status = getAppCommand.doIt();
					App app = getAppCommand.getApp();

					if (CFProtocolConstants.KEY_STARTED.equals(state)) {
						if (!status.isOK())
							return status;
						return new StartAppCommand(target, app).doIt();
					} else if (CFProtocolConstants.KEY_STOPPED.equals(state)) {
						if (!status.isOK())
							return status;
						return new StopAppCommand(target, app).doIt();
					}

					// push new application
					if (app == null)
						app = new App();

					app.setName(appName);

					/* parse the application manifest */
					ParseManifestCommand parseManifestCommand = new ParseManifestCommand(target, app, this.userId, contentLocation);
					status = parseManifestCommand.doIt();
					if (!status.isOK())
						return status;

					status = new PushAppCommand(target, app).doIt();
					if (!status.isOK())
						return status;

					getAppCommand = new GetAppCommand(target, app.getName());
					getAppCommand.doIt();
					app = getAppCommand.getApp();

					new StartAppCommand(target, app).doIt();

					return status;
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	private IStatus getApps(Target target) throws Exception {
		String appsUrl = target.getSpace().getCFJSON().getJSONObject("entity").getString("apps_url");
		appsUrl = appsUrl.replaceAll("apps", "summary");
		URI appsURI = URIUtil.toURI(target.getUrl()).resolve(appsUrl);

		GetMethod getMethod = new GetMethod(appsURI.toString());
		HttpUtil.configureHttpMethod(getMethod, target);
		return HttpUtil.executeMethod(getMethod);
	}
}
