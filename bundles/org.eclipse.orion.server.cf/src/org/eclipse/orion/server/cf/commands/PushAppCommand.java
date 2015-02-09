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

import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.ds.IDeploymentPackager;
import org.eclipse.orion.server.cf.ext.ICFDeploymentExtService;
import org.eclipse.orion.server.cf.ext.ICFEnvironmentExtService;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushAppCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private App app;
	private IFileStore appStore;
	private String commandName;
	private IDeploymentPackager packager;

	public PushAppCommand(Target target, App app, IFileStore appStore, IDeploymentPackager packager) {
		super(target);
		this.commandName = "Push application"; //$NON-NLS-1$

		this.app = app;
		this.appStore = appStore;
		this.packager = packager;
	}

	@Override
	protected ServerStatus _doIt() {

		/* multi server status */
		MultiServerStatus status = new MultiServerStatus();
		GetAppCommand.expire(target, app.getName());
		try {

			/* set up the application */
			AbstractCFCommand setUpApplication = null;
			if (app.getSummaryJSON() != null) {

				/* set known application guid */
				app.setGuid(app.getSummaryJSON().getString(CFProtocolConstants.V2_KEY_GUID));
				setUpApplication = new UpdateApplicationCommand(target, app);

			} else
				setUpApplication = new CreateApplicationCommand(target, app);

			ServerStatus jobStatus = (ServerStatus) setUpApplication.doIt(); /* FIXME: unsafe type cast */
			status.add(jobStatus);
			if (!jobStatus.isOK())
				return status;

			JSONObject respAppJSON = jobStatus.getJsonData();

			/* look up available environment extension services */
			ICFDeploymentExtService deploymentExtService = CFActivator.getDefault().getCFDeploymentExtDeploymentService();
			if (deploymentExtService != null) {

				ICFEnvironmentExtService environmentExtService = deploymentExtService.getDefaultEnvironmentExtService();
				if (environmentExtService != null && environmentExtService.applies(app.getManifest())) {
					ServerStatus envExtStatus = environmentExtService.apply(target, app);
					status.add(envExtStatus);
					if (!envExtStatus.isOK())
						return status;
				}
			}

			/* set up the application route */
			BindRouteCommand bindRoute = new BindRouteCommand(target, app);
			ServerStatus multijobStatus = (ServerStatus) bindRoute.doIt(); /* FIXME: unsafe type cast */
			status.add(multijobStatus);
			if (!multijobStatus.isOK())
				return status;

			/* upload application contents */
			UploadBitsCommand uploadBits = new UploadBitsCommand(target, app, appStore, packager);
			multijobStatus = (ServerStatus) uploadBits.doIt(); /* FIXME: unsafe type cast */
			status.add(multijobStatus);
			if (!multijobStatus.isOK())
				return status;

			/* bind application specific services */
			BindServicesCommand bindServices = new BindServicesCommand(target, app);
			multijobStatus = (ServerStatus) bindServices.doIt(); /* FIXME: unsafe type cast */
			status.add(multijobStatus);
			if (!multijobStatus.isOK())
				return status;

			/* extract user defined timeout if present */
			ManifestParseTree manifest = app.getManifest();
			ManifestParseTree timeoutNode = manifest.get(CFProtocolConstants.V2_KEY_APPLICATIONS).get(0).getOpt(CFProtocolConstants.V2_KEY_TIMEOUT);
			int timeout = (timeoutNode != null) ? Integer.parseInt(timeoutNode.getValue()) : ManifestConstants.DEFAULT_TIMEOUT;

			/* craft command result */
			JSONObject result = new JSONObject();
			result.put("Target", target.toJSON()); //$NON-NLS-1$
			if (target.getManageUrl() != null)
				result.put("ManageUrl", target.getManageUrl().toString() + "#/resources/appGuid=" + app.getGuid() + "&orgGuid=" + target.getOrg().getGuid() + "&spaceGuid=" + target.getSpace().getGuid()); //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$
			result.put("App", respAppJSON); //$NON-NLS-1$
			result.put("Domain", bindRoute.getDomainName()); //$NON-NLS-1$
			result.put("Route", bindRoute.getRoute()); //$NON-NLS-1$
			result.put("Timeout", timeout); //$NON-NLS-1$
			result.put("DeployedPackage", uploadBits.getDeployedAppPackageName()); //$NON-NLS-1$

			status.add(new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result));
			return status;

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return status;
		}
	}
}
