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
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.commands.*;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
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
		final String location = IOUtilities.getQueryParameter(request, "location");
		final String name = IOUtilities.getQueryParameter(request, "name");

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					Target target = CFActivator.getDefault().getTargetMap().getTarget(userId);
					if (target == null) {
						String msg = "Target not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					if (target.getSpace() == null) {
						String msg = "Space not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					if (location != null || name != null) {
						return getApp(target, name, location);
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
	protected CFJob handlePost(App resource, HttpServletRequest request, HttpServletResponse response, final String path) {
		final JSONObject jsonData = extractJSONData(request);

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					Target target = CFActivator.getDefault().getTargetMap().getTarget(userId);
					if (target == null) {
						String msg = "Target not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					if (target.getSpace() == null) {
						String msg = "Space not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					String state = jsonData.getString(CFProtocolConstants.KEY_STATE);
					String name = jsonData.getString(CFProtocolConstants.KEY_NAME);
					String dir = jsonData.getString(CFProtocolConstants.KEY_DIR);

					// TODO if name is null, try to compute the name

					if (CFProtocolConstants.KEY_STARTED.equals(state)) {
						return new StartAppCommand(target, name).doIt();
					} else if (CFProtocolConstants.KEY_STOPPED.equals(state)) {
						return new StopAppCommand(target, name).doIt();
					}
					return new PushAppCommand(target, name).doIt();
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	private IStatus getApp(Target target, String name, String location) throws Exception {
		URI appsURI = URIUtil.toURI(target.getUrl());
		String appsUrl = target.getSpace().getJSONObject("entity").getString("apps_url");
		appsURI = appsURI.resolve(appsUrl);

		GetMethod getMethod = new GetMethod(appsURI.toString());
		getMethod.addRequestHeader(new Header("Accept", "application/json"));
		getMethod.addRequestHeader(new Header("Content-Type", "application/json"));
		if (target.getAccessToken() != null)
			getMethod.addRequestHeader(new Header("Authorization", "bearer " + target.getAccessToken().getString("access_token")));
		getMethod.setQueryString("q=name:" + name + "&inline-relations-depth=1");

		CFActivator.getDefault().getHttpClient().executeMethod(getMethod);

		String response = getMethod.getResponseBodyAsString();
		JSONObject apps = new JSONObject(response);
		JSONObject app = apps.getJSONArray("resources").getJSONObject(0).getJSONObject("metadata");

		URI summaryAppURI = URIUtil.toURI(target.getUrl());
		String summaryAppUrl = app.getString("url") + "/summary";
		summaryAppURI = summaryAppURI.resolve(summaryAppUrl);

		GetMethod getMethod2 = new GetMethod(appsURI.toString());
		getMethod2.addRequestHeader(new Header("Accept", "application/json"));
		getMethod2.addRequestHeader(new Header("Content-Type", "application/json"));
		if (target.getAccessToken() != null)
			getMethod2.addRequestHeader(new Header("Authorization", "bearer " + target.getAccessToken().getString("access_token")));

		CFActivator.getDefault().getHttpClient().executeMethod(getMethod2);
		response = getMethod.getResponseBodyAsString();
		JSONObject summary = new JSONObject(response);

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, summary.getJSONArray("resources").getJSONObject(0).getJSONObject("entity"));
	}

	private IStatus getApps(Target target) throws Exception {
		URI appsURI = URIUtil.toURI(target.getUrl());
		String appsUrl = target.getSpace().getJSONObject("entity").getString("apps_url");
		appsUrl = appsUrl.replaceAll("apps", "summary");
		appsURI = appsURI.resolve(appsUrl);

		GetMethod getMethod = new GetMethod(appsURI.toString());
		getMethod.addRequestHeader(new Header("Accept", "application/json"));
		getMethod.addRequestHeader(new Header("Content-Type", "application/json"));
		if (target.getAccessToken() != null)
			getMethod.addRequestHeader(new Header("Authorization", "bearer " + target.getAccessToken().getString("access_token")));
		CFActivator.getDefault().getHttpClient().executeMethod(getMethod);

		String response = getMethod.getResponseBodyAsString();
		JSONObject result = new JSONObject(response);

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
	}
}
