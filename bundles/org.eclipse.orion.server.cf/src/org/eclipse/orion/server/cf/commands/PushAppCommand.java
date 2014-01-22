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
import java.util.Scanner;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.*;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.MultiServerStatus;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushAppCommand extends AbstractCFMultiCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App app;

	public PushAppCommand(Target target, App app) {
		super(target);
		this.commandName = "Push application"; //$NON-NLS-1$
		this.app = app;
	}

	@Override
	protected MultiServerStatus _doIt() {
		/* multi server status */
		MultiServerStatus status = new MultiServerStatus();

		try {
			URI targetURI = URIUtil.toURI(target.getUrl());
			JSONObject manifestJSON = null;

			try {
				/* parse manifest if present */
				manifestJSON = parseManifest(app.getAppStore(), targetURI);
			} catch (ParseException ex) {
				/* parse error, fail */
				status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage(), ex));
				return status;
			}

			if (manifestJSON == null) {
				status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Failed to find application manifest", null));
				return status;
			}

			if (app.getSummaryJSON() == null) {

				/* create new application */
				CreateApplicationCommand createApplication = new CreateApplicationCommand(target, manifestJSON);
				ServerStatus jobStatus = (ServerStatus) createApplication.doIt(); /* TODO: unsafe type cast */
				status.add(jobStatus);
				if (!jobStatus.isOK())
					return status;

				/* extract application guid */
				JSONObject appResp = jobStatus.getJsonData();
				app.setGuid(appResp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));
				app.setName(manifestJSON.getJSONArray(CFProtocolConstants.V2_KEY_APPLICATIONS).getJSONObject(0).getString(CFProtocolConstants.V2_KEY_NAME));

				/* bind route to application */
				BindRouteCommand bindRoute = new BindRouteCommand(target, app, manifestJSON);
				MultiServerStatus multijobStatus = (MultiServerStatus) bindRoute.doIt(); /* TODO: unsafe type cast */
				status.add(multijobStatus);
				if (!multijobStatus.isOK())
					return status;

				/* upload project contents */
				UploadBitsCommand uploadBits = new UploadBitsCommand(target, app, manifestJSON);
				multijobStatus = (MultiServerStatus) uploadBits.doIt(); /* TODO: unsafe type cast */
				status.add(multijobStatus);
				if (!multijobStatus.isOK())
					return status;

				/* bind application specific services */
				BindServicesCommand bindServices = new BindServicesCommand(target, app, manifestJSON);
				multijobStatus = (MultiServerStatus) bindServices.doIt(); /* TODO: unsafe type cast */
				status.add(multijobStatus);
				if (!multijobStatus.isOK())
					return status;

				/* craft command result */
				JSONObject result = new JSONObject();
				result.put("Target", target.toJSON());
				if (target.getManageUrl() != null)
					result.put("ManageUrl", target.getManageUrl().toString() + "#/resources/appGuid=" + app.getGuid());
				result.put("App", appResp);
				result.put("Domain", bindRoute.getDomainName());
				result.put("Route", bindRoute.getRoute());

				status.add(new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result));
				return status;
			}

			/* set known application guid */
			app.setGuid(app.getSummaryJSON().getString(CFProtocolConstants.V2_KEY_GUID));

			/* upload project contents */
			UploadBitsCommand uploadBits = new UploadBitsCommand(target, app, manifestJSON);
			MultiServerStatus multijobStatus = (MultiServerStatus) uploadBits.doIt(); /* TODO: unsafe type cast */
			status.add(multijobStatus);
			if (!multijobStatus.isOK())
				return status;

			/* restart the application */
			RestartAppCommand restartApp = new RestartAppCommand(target, app);
			multijobStatus = (MultiServerStatus) restartApp.doIt(); /* TODO: unsafe type cast */
			status.add(multijobStatus);
			if (!multijobStatus.isOK())
				return status;

			/* craft command result */
			JSONObject route = app.getSummaryJSON().getJSONArray("routes").getJSONObject(0);
			JSONObject result = new JSONObject();

			result.put("Target", target.toJSON());
			if (target.getManageUrl() != null)
				result.put("ManageUrl", target.getManageUrl().toString() + "#/resources/appGuid=" + app.getGuid());
			result.put("App", app.getSummaryJSON());
			result.put("Domain", route.getJSONObject("domain").getString("name"));
			result.put("Route", route);

			status.add(new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result));
			return status;

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return status;
		}
	}

	private JSONObject parseManifest(IFileStore appStore, URI targetURI) throws ParseException, CoreException {
		if (appStore == null)
			return null;

		/* lookup the manifest description */
		IFileStore manifestStore = appStore.getChild(ManifestUtils.MANIFEST_FILE_NAME);
		if (!manifestStore.fetchInfo().exists())
			return null;

		Scanner manifestScanner = new Scanner(manifestStore.openInputStream(EFS.NONE, null));
		ManifestNode manifestTree = ManifestParser.parse(manifestScanner);
		manifestScanner.close();

		/* parse within the context of target */
		return manifestTree.toJSON(targetURI);
	}
}
