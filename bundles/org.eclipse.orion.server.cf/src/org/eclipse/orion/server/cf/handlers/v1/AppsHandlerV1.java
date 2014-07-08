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
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.commands.*;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.IOUtilities;
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
	protected CFJob handlePut(App resource, HttpServletRequest request, HttpServletResponse response, final String path) {
		final JSONObject jsonData = extractJSONData(request);

		final String state = jsonData.optString(CFProtocolConstants.KEY_STATE, null);
		final String appName = jsonData.optString(CFProtocolConstants.KEY_NAME, null);
		final JSONObject targetJSON = jsonData.optJSONObject(CFProtocolConstants.KEY_TARGET);
		final String contentLocation = ServletResourceHandler.toOrionLocation(request, jsonData.optString(CFProtocolConstants.KEY_CONTENT_LOCATION, null));

		/* default application startup is one minute */
		int userTimeout = jsonData.optInt(CFProtocolConstants.KEY_TIMEOUT, 60);
		final int timeout = (userTimeout > 0) ? userTimeout : 0;

		/* TODO: The force shouldn't be always with us */
		final boolean force = jsonData.optBoolean(CFProtocolConstants.KEY_FORCE, true);

		/* non-manifest deployment parameters */
		final boolean nonManifest = jsonData.optBoolean(CFProtocolConstants.KEY_NON_MANIFEST, false);
		final boolean persist = jsonData.optBoolean(CFProtocolConstants.KEY_PERSIST, false);

		return new CFJob(request, false) {

			@Override
			protected IStatus performJob() {

				try {

					ComputeTargetCommand computeTarget = new ComputeTargetCommand(userId, targetJSON);
					IStatus status = computeTarget.doIt();
					if (!status.isOK())
						return status;

					Target target = computeTarget.getTarget();

					/* manifest name has a higher priority than the parameter name */
					String applicationName = appName;

					/* parse the application manifest */
					ManifestParseTree manifest = null;
					IFileStore applicationStore = null;

					if (contentLocation != null) {

						ParseManifestCommand parseManifestCommand = new ParseManifestCommand(target, userId, contentLocation);
						status = parseManifestCommand.doIt();

						if (!status.isOK() && parseManifestCommand.hasMissingManifest()) {

							/* try to generate an appropriate deployment description */
							GetDeploymentDescriptionCommand deploymentDescriptionCommand = new GetDeploymentDescriptionCommand(target, userId, applicationName, contentLocation);
							IStatus descriptionStatus = deploymentDescriptionCommand.doIt();
							if (!descriptionStatus.isOK())
								return status;

							if (!nonManifest) {

								/* client ordered a manifest deployment, without an actual manifest.
								 * We recognized the application type and should indicate this fact. */

								String applicationType = deploymentDescriptionCommand.getApplicationType();
								String msg = NLS.bind("Could not find the application manifest. " + //
										"Project contents indicate the following application type \"{0}\". " //
										+ "Perhaps you wish to continue with a non-manifest deployment?", //
										applicationType);

								JSONObject response = new JSONObject();
								response.put(CFProtocolConstants.V2_KEY_ERROR_CODE, "CF-MissingManifest");
								response.put(CFProtocolConstants.V2_KEY_APPLICATION_TYPE, applicationType);
								response.put(CFProtocolConstants.V2_KEY_ERROR_DESCRIPTION, msg);

								return new ServerStatus(IStatus.WARNING, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, response, null);
							}

							/* parse using the deployment description */
							parseManifestCommand = new ParseManifestCommand(target, userId, contentLocation, deploymentDescriptionCommand.getManifest());
							status = parseManifestCommand.doIt();
							if (!status.isOK())
								return status;

						} else if (!status.isOK())
							return status;

						/* gather application deployment data */
						manifest = parseManifestCommand.getManifest();
						applicationStore = parseManifestCommand.getAppStore();

						if (manifest != null) {

							if (nonManifest && persist)
								manifest.persist(applicationStore.getChild(ManifestConstants.MANIFEST_FILE_NAME));

							ManifestParseTree applications = manifest.get(CFProtocolConstants.V2_KEY_APPLICATIONS);
							if (applications.getChildren().size() > 0)
								applicationName = applications.get(0).get(CFProtocolConstants.V2_KEY_NAME).getValue();
						}
					}

					GetAppCommand getAppCommand = new GetAppCommand(target, applicationName);
					status = getAppCommand.doIt();
					App app = getAppCommand.getApp();

					if (CFProtocolConstants.KEY_STARTED.equals(state)) {
						if (!status.isOK())
							return status;
						return new StartAppCommand(target, app, timeout).doIt();
					} else if (CFProtocolConstants.KEY_STOPPED.equals(state)) {
						if (!status.isOK())
							return status;
						return new StopAppCommand(target, app).doIt();
					} else {
						if (manifest == null) {
							String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
							status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, null);
							logger.error(msg);
							return status;
						}
					}

					/* push new application */
					if (app == null)
						app = new App();

					app.setName(applicationName);
					app.setManifest(manifest);
					app.setAppStore(applicationStore);

					status = new PushAppCommand(target, app, force).doIt();
					if (!status.isOK())
						return status;

					/* get the application again */
					getAppCommand = new GetAppCommand(target, app.getName());
					getAppCommand.doIt();
					app = getAppCommand.getApp();
					app.setManifest(manifest);

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
