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
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.*;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushAppCommand extends AbstractCFCommand {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String commandName;
	private App app;

	public PushAppCommand(String userId, Target target, App app) {
		super(target, userId);
		this.commandName = "Push application"; //$NON-NLS-1$
		this.app = app;
	}

	public ServerStatus _doIt() {
		try {

			URI targetURI = URIUtil.toURI(target.getUrl());
			JSONObject manifestJSON = null;

			try {
				/* parse manifest if present */
				manifestJSON = parseManifest(this.app.getContentLocation(), targetURI);
			} catch (ParseException ex) {
				/* parse error, fail */
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_BAD_REQUEST);
			}

			if (manifestJSON == null)
				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_BAD_REQUEST);

			if (app.getSummaryJSON() == null) {

				/* create new application */
				CreateApplicationCommand createApplication = new CreateApplicationCommand(userId, target, manifestJSON);
				ServerStatus jobStatus = (ServerStatus) createApplication.doIt(); /* TODO: unsafe type cast */
				if (!jobStatus.isOK())
					return jobStatus;

				/* extract application guid */
				JSONObject appResp = jobStatus.getJsonData();
				app.setGuid(appResp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_GUID));

				/* bind route to application */
				BindRouteCommand bindRoute = new BindRouteCommand(userId, target, app, manifestJSON);
				jobStatus = (ServerStatus) bindRoute.doIt(); /* TODO: unsafe type cast */
				if (!jobStatus.isOK())
					return jobStatus;

				/* upload project contents */
				UploadBitsCommand uploadBits = new UploadBitsCommand(userId, target, app);
				jobStatus = (ServerStatus) uploadBits.doIt(); /* TODO: unsafe type cast */
				if (!jobStatus.isOK())
					return jobStatus;

				/* bind application specific services */
				BindServicesCommand bindServices = new BindServicesCommand(userId, target, app, manifestJSON);
				jobStatus = (ServerStatus) bindServices.doIt(); /* TODO: unsafe type cast */
				if (!jobStatus.isOK())
					return jobStatus;

				/* craft command result */
				JSONObject result = new JSONObject();
				result.put("Target", target.toJSON());
				result.put("ManageUrl", target.getUrl().toString() + "#/resources/appGuid=" + app.getGuid());
				result.put("App", appResp);
				result.put("Domain", bindRoute.getDomainName());
				result.put("Route", bindRoute.getRoute());

				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
			}

			/* set known application guid */
			app.setGuid(app.getSummaryJSON().getString(CFProtocolConstants.V2_KEY_GUID));

			/* upload project contents */
			UploadBitsCommand uploadBits = new UploadBitsCommand(userId, target, app);
			ServerStatus jobStatus = (ServerStatus) uploadBits.doIt(); /* TODO: unsafe type cast */
			if (!jobStatus.isOK())
				return jobStatus;

			/* restart the application */
			RestartAppCommand restartApp = new RestartAppCommand(userId, target, app);
			jobStatus = (ServerStatus) restartApp.doIt(); /* TODO: unsafe type cast */
			if (!jobStatus.isOK())
				return jobStatus;

			/* craft command result */
			JSONObject route = app.getSummaryJSON().getJSONArray("routes").getJSONObject(0);
			JSONObject result = new JSONObject();

			result.put("Target", target.toJSON());
			result.put("ManageUrl", target.getUrl().toString() + "#/resources/appGuid=" + app.getGuid());
			result.put("App", app.getSummaryJSON());
			result.put("Domain", route.getJSONObject("domain").getString("name"));
			result.put("Route", route);

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}

	private JSONObject parseManifest(String sourcePath, URI targetURI) throws ParseException, CoreException {
		/* get the underlying file store */
		IFileStore fileStore = NewFileServlet.getFileStore(null, new Path(sourcePath).removeFirstSegments(1));
		if (fileStore == null)
			return null;

		/* lookup the manifest description */
		IFileStore manifestStore = fileStore.getChild(ManifestUtils.MANIFEST_FILE_NAME);
		if (!manifestStore.fetchInfo().exists())
			return null;

		Scanner manifestScanner = new Scanner(manifestStore.openInputStream(EFS.NONE, null));
		ManifestNode manifestTree = ManifestParser.parse(manifestScanner);
		manifestScanner.close();

		/* parse within the context of target */
		return manifestTree.toJSON(targetURI);
	}
}
