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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.commands.*;
import org.eclipse.orion.server.cf.ds.IDeploymentPackager;
import org.eclipse.orion.server.cf.ds.IDeploymentService;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.cf.objects.*;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.*;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
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
		return null;
	}

	@Override
	protected CFJob handleGet(App app, HttpServletRequest request, HttpServletResponse response, final String path) {

		final String encodedContentLocation = IOUtilities.getQueryParameter(request, ProtocolConstants.KEY_CONTENT_LOCATION);
		String contentLocation = null;
		if (encodedContentLocation != null) {
			try {
				contentLocation = ServletResourceHandler.toOrionLocation(request, URLDecoder.decode(encodedContentLocation, "UTF8"));
			} catch (UnsupportedEncodingException e) {
				// do nothing
			}
		}
		final String finalContentLocation = contentLocation;

		final String encodedName = IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_NAME);
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

					if (encodedName != null) {
						String name = URLDecoder.decode(encodedName, "UTF8");
						return new GetAppCommand(target, name).doIt();
					} else if (encodedContentLocation != null) {
						if (finalContentLocation == null)
							return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Can not determine the application name", null);

						/* parse the application manifest */
						String manifestAppName = null;
						ParseManifestCommand parseManifestCommand = new ParseManifestCommand(target, this.userId, finalContentLocation);
						IStatus status = parseManifestCommand.doIt();
						if (!status.isOK())
							return status;

						/* get the manifest name */
						ManifestParseTree manifest = parseManifestCommand.getManifest();
						if (manifest != null) {
							ManifestParseTree applications = manifest.get(CFProtocolConstants.V2_KEY_APPLICATIONS);
							if (applications.getChildren().size() > 0) {
								manifestAppName = applications.get(0).get(CFProtocolConstants.V2_KEY_NAME).getValue();
								return new GetAppCommand(target, manifestAppName).doIt();
							}
						}

						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Can not determine the application name", null);
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
	protected CFJob handlePut(App resource, HttpServletRequest request, HttpServletResponse response, final String pathString) {
		final JSONObject targetJSON2 = extractJSONData(IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_TARGET));

		IPath path = pathString != null ? new Path(pathString) : new Path(""); //$NON-NLS-1$
		final String appGuid = path.segment(0);
		boolean addRoute = "routes".equals(path.segment(1)); //$NON-NLS-1$
		final String routeGuid = addRoute ? path.segment(2) : null;

		if (addRoute)
			return new CFJob(request, false) {
				@Override
				protected IStatus performJob() {
					try {
						ComputeTargetCommand computeTarget = new ComputeTargetCommand(this.userId, targetJSON2);
						IStatus status = computeTarget.doIt();
						if (!status.isOK())
							return status;
						Target target = computeTarget.getTarget();

						GetAppByGuidCommand getAppByGuid = new GetAppByGuidCommand(target.getCloud(), appGuid);
						IStatus getAppByGuidStatus = getAppByGuid.doIt();
						if (!getAppByGuidStatus.isOK())
							return getAppByGuidStatus;
						App app = getAppByGuid.getApp();

						GetRouteByGuidCommand getRouteByGuid = new GetRouteByGuidCommand(target.getCloud(), routeGuid);
						IStatus getRouteByGuidStatus = getRouteByGuid.doIt();
						if (!getRouteByGuidStatus.isOK())
							return getRouteByGuidStatus;
						Route route = getRouteByGuid.getRoute();

						MapRouteCommand unmapRoute = new MapRouteCommand(target, app, route.getGuid());
						return unmapRoute.doIt();
					} catch (Exception e) {
						String msg = NLS.bind("Failed to handle request for {0}", pathString); //$NON-NLS-1$
						ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
						logger.error(msg, e);
						return status;
					}
				}
			};

		final JSONObject jsonData = extractJSONData(request);
		final JSONObject targetJSON = jsonData.optJSONObject(CFProtocolConstants.KEY_TARGET);

		final String state = jsonData.optString(CFProtocolConstants.KEY_STATE, null);
		final String appName = jsonData.optString(CFProtocolConstants.KEY_NAME, null);
		final String contentLocation = ServletResourceHandler.toOrionLocation(request, jsonData.optString(CFProtocolConstants.KEY_CONTENT_LOCATION, null));

		/* custom packager details */
		final String packagerName = jsonData.optString(CFProtocolConstants.KEY_PACKAGER, null);

		/* non-manifest deployments using a .json representation */
		final JSONObject manifestJSON = jsonData.optJSONObject(CFProtocolConstants.KEY_MANIFEST);
		final JSONObject instrumentationJSON = jsonData.optJSONObject(CFProtocolConstants.KEY_INSTRUMENTATION);
		final boolean persistManifest = jsonData.optBoolean(CFProtocolConstants.KEY_PERSIST, false);

		/* default application startup is one minute */
		int userTimeout = jsonData.optInt(CFProtocolConstants.KEY_TIMEOUT, 60);
		final int timeout = (userTimeout > 0) ? userTimeout : 0;

		return new CFJob(request, false) {

			@Override
			protected IStatus performJob() {
				try {

					ComputeTargetCommand computeTarget = new ComputeTargetCommand(userId, targetJSON);
					IStatus status = computeTarget.doIt();
					if (!status.isOK())
						return status;

					Target target = computeTarget.getTarget();

					/* parse the application manifest */
					String manifestAppName = null;
					ManifestParseTree manifest = null;
					IFileStore appStore = null;

					if (contentLocation != null && state == null) {

						/* check for non-manifest deployment */
						if (manifestJSON != null) {

							ParseManifestJSONCommand parseManifestJSONCommand = new ParseManifestJSONCommand(manifestJSON, userId, contentLocation);
							status = parseManifestJSONCommand.doIt();
							if (!status.isOK())
								return status;

							/* get the manifest name */
							manifest = parseManifestJSONCommand.getManifest();
							appStore = parseManifestJSONCommand.getAppStore();

							if (manifest != null) {
								ManifestParseTree applications = manifest.get(CFProtocolConstants.V2_KEY_APPLICATIONS);
								if (applications.getChildren().size() > 0)
									manifestAppName = applications.get(0).get(CFProtocolConstants.V2_KEY_NAME).getValue();

								if (persistManifest) {
									/* non-manifest deployment - persist at contentLocation/manifest.yml */
									IFileStore persistBaseLocation = parseManifestJSONCommand.getPersistBaseLocation();
									IFileStore persistLocation = persistBaseLocation.getChild(ManifestConstants.MANIFEST_FILE_NAME);
									manifest.persist(persistLocation);
								}
							}

						} else {

							ParseManifestCommand parseManifestCommand = new ParseManifestCommand(target, userId, contentLocation);
							status = parseManifestCommand.doIt();
							if (!status.isOK())
								return status;

							/* get the manifest name */
							manifest = parseManifestCommand.getManifest();
							appStore = parseManifestCommand.getAppStore();

							if (manifest != null) {
								ManifestParseTree applications = manifest.get(CFProtocolConstants.V2_KEY_APPLICATIONS);
								if (applications.getChildren().size() > 0)
									manifestAppName = applications.get(0).get(CFProtocolConstants.V2_KEY_NAME).getValue();
							}

						}
					}

					GetAppCommand getAppCommand = new GetAppCommand(target, appName != null ? appName : manifestAppName);
					status = getAppCommand.doIt();
					App app = getAppCommand.getApp();

					if (CFProtocolConstants.KEY_STARTED.equals(state)) {
						if (!status.isOK())
							return status;

						// StartDebugAppCommand startDebugAppCommand = new StartDebugAppCommand(app);
						// ServerStatus startDebugAppStatus = (ServerStatus) startDebugAppCommand.doIt();
						// if (startDebugAppStatus.isOK())
						//	return startDebugAppStatus;

						return new StartAppCommand(target, app, timeout).doIt();
					} else if (CFProtocolConstants.KEY_STOPPED.equals(state)) {
						if (!status.isOK())
							return status;

						// StopDebugAppCommand stopDebugAppCommand = new StopDebugAppCommand(app);
						// ServerStatus stopDebugAppStatus = (ServerStatus) stopDebugAppCommand.doIt();
						// if (stopDebugAppStatus.isOK())
						//	return stopDebugAppStatus;

						return new StopAppCommand(target, app).doIt();
					} else {
						if (manifest == null) {
							String msg = NLS.bind("Failed to handle request for {0}", pathString); //$NON-NLS-1$
							status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, null);
							logger.error(msg);
							return status;
						}
					}

					/* indicates whether to restart the application
					 * or just start it as it's a new deployment */
					boolean restart = true;

					if (app == null) {
						/* push new application */
						app = new App();
						restart = false;
					}

					/* instrument the manifest if required */
					ManifestUtils.instrumentManifest(manifest, instrumentationJSON);

					app.setName(appName != null ? appName : manifestAppName);
					app.setManifest(manifest);

					/* determine deployment packager */
					IDeploymentService deploymentService = CFActivator.getDefault().getDeploymentService();
					IDeploymentPackager packager = deploymentService.getDeploymentPackager(packagerName);
					if (packager == null)
						packager = deploymentService.getDefaultDeplomentPackager();

					status = new PushAppCommand(target, app, appStore, packager).doIt();

					if (!status.isOK())
						return status;

					// get the app again
					getAppCommand = new GetAppCommand(target, app.getName());
					getAppCommand.doIt();
					app = getAppCommand.getApp();
					app.setManifest(manifest);

					ServerStatus startStatus = null;
					if (restart)
						startStatus = (ServerStatus) new RestartAppCommand(target, app).doIt();
					else
						startStatus = (ServerStatus) new StartAppCommand(target, app).doIt();

					if (startStatus.getSeverity() == IStatus.ERROR) {
						return startStatus;
					}

					return status;
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", pathString); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	@Override
	protected CFJob handleDelete(App resource, HttpServletRequest request, HttpServletResponse response, final String pathString) {
		final JSONObject targetJSON = extractJSONData(IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_TARGET));

		IPath path = new Path(pathString);
		final String appGuid = path.segment(0);
		boolean deleteRoute = "routes".equals(path.segment(1)); //$NON-NLS-1$
		final String routeGuid = deleteRoute ? path.segment(2) : null;

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					ComputeTargetCommand computeTarget = new ComputeTargetCommand(userId, targetJSON);
					IStatus status = computeTarget.doIt();
					if (!status.isOK())
						return status;
					Target target = computeTarget.getTarget();

					GetAppByGuidCommand getAppByGuid = new GetAppByGuidCommand(target.getCloud(), appGuid);
					IStatus getAppByGuidStatus = getAppByGuid.doIt();
					if (!getAppByGuidStatus.isOK())
						return getAppByGuidStatus;
					App app = getAppByGuid.getApp();

					GetRouteByGuidCommand getRouteByGuid = new GetRouteByGuidCommand(target.getCloud(), routeGuid);
					IStatus getRouteByGuidStatus = getRouteByGuid.doIt();
					if (!getRouteByGuidStatus.isOK())
						return getRouteByGuidStatus;
					Route route = getRouteByGuid.getRoute();

					UnmapRouteCommand unmapRoute = new UnmapRouteCommand(target, app, route);
					return unmapRoute.doIt();
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", pathString); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	private IStatus getApps(Target target) throws Exception {
		String appsUrl = target.getSpace().getCFJSON().getJSONObject("entity").getString("apps_url"); //$NON-NLS-1$//$NON-NLS-2$
		URI appsURI = URIUtil.toURI(target.getUrl()).resolve(appsUrl);

		GetMethod getAppsMethod = new GetMethod(appsURI.toString());
		HttpUtil.configureHttpMethod(getAppsMethod, target.getCloud());
		getAppsMethod.setQueryString("inline-relations-depth=2"); //$NON-NLS-1$

		ServerStatus getAppsStatus = HttpUtil.executeMethod(getAppsMethod);
		if (!getAppsStatus.isOK())
			return getAppsStatus;

		/* extract available apps */
		JSONObject appsJSON = getAppsStatus.getJsonData();

		JSONObject result = new JSONObject();
		result.put("Apps", new JSONArray()); //$NON-NLS-1$

		if (appsJSON.getInt(CFProtocolConstants.V2_KEY_TOTAL_RESULTS) < 1) {
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		}

		JSONArray resources = appsJSON.getJSONArray(CFProtocolConstants.V2_KEY_RESOURCES);
		for (int k = 0; k < resources.length(); ++k) {
			JSONObject appJSON = resources.getJSONObject(k);
			App2 app = new App2();
			app.setCFJSON(appJSON);
			result.append("Apps", app.toJSON()); //$NON-NLS-1$
		}

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
	}
}
