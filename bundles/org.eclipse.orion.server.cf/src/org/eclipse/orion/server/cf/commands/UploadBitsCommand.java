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

import java.io.File;
import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.multipart.*;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.ds.IDeploymentPackager;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.utils.*;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UploadBitsCommand extends AbstractCFApplicationCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$
	private static final int MAX_ATTEMPTS = 150;

	private String commandName;
	private IFileStore appStore;
	private String deployedAppPackageName;

	private IDeploymentPackager packager;

	public UploadBitsCommand(Target target, App app, IFileStore appStore, IDeploymentPackager packager) {
		super(target, app);

		String[] bindings = {app.getName(), app.getGuid()};
		this.commandName = NLS.bind("Upload application {0} bits (guid: {1})", bindings);
		this.appStore = appStore;
		this.packager = packager;
	}

	public String getDeployedAppPackageName() {
		return deployedAppPackageName;
	}

	@Override
	protected ServerStatus _doIt() {
		/* multi server status */
		MultiServerStatus status = new MultiServerStatus();

		try {

			/* upload project contents */
			File appPackage = packager.getDeploymentPackage(appStore);
			deployedAppPackageName = PackageUtils.getApplicationPackageType(appStore);

			if (appPackage == null) {
				status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to read application content", null));
				return status;
			}

			URI targetURI = URIUtil.toURI(target.getUrl());
			PutMethod uploadMethod = new PutMethod(targetURI.resolve("/v2/apps/" + getApplication().getGuid() + "/bits?async=true").toString());
			uploadMethod.addRequestHeader(new Header("Authorization", "bearer " + target.getCloud().getAccessToken().getString("access_token")));

			Part[] parts = {new StringPart(CFProtocolConstants.V2_KEY_RESOURCES, "[]"), new FilePart(CFProtocolConstants.V2_KEY_APPLICATION, appPackage)};
			uploadMethod.setRequestEntity(new MultipartRequestEntity(parts, uploadMethod.getParams()));

			/* send request */
			ServerStatus jobStatus = HttpUtil.executeMethod(uploadMethod);
			status.add(jobStatus);
			if (!jobStatus.isOK())
				return status;

			/* long running task, keep track */
			int attemptsLeft = MAX_ATTEMPTS;
			JSONObject resp = jobStatus.getJsonData();
			String taksStatus = resp.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_STATUS);
			while (!CFProtocolConstants.V2_KEY_FINISHED.equals(taksStatus) && !CFProtocolConstants.V2_KEY_FAILURE.equals(taksStatus)) {
				if (CFProtocolConstants.V2_KEY_FAILED.equals(taksStatus)) {
					/* delete the tmp file */
					appPackage.delete();
					status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Upload failed", null));
					return status;
				}

				if (attemptsLeft == 0) {
					/* delete the tmp file */
					appPackage.delete();
					status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Upload timeout exceeded", null));
					return status;
				}

				/* two seconds */
				Thread.sleep(2000);

				/* check whether job has finished */
				URI jobLocation = targetURI.resolve(resp.getJSONObject(CFProtocolConstants.V2_KEY_METADATA).getString(CFProtocolConstants.V2_KEY_URL));
				GetMethod jobRequest = new GetMethod(jobLocation.toString());
				HttpUtil.configureHttpMethod(jobRequest, target.getCloud());

				/* send request */
				jobStatus = HttpUtil.executeMethod(jobRequest);
				status.add(jobStatus);
				if (!jobStatus.isOK())
					return status;

				resp = jobStatus.getJsonData();
				taksStatus = resp.getJSONObject(CFProtocolConstants.V2_KEY_ENTITY).getString(CFProtocolConstants.V2_KEY_STATUS);

				--attemptsLeft;
			}

			if (CFProtocolConstants.V2_KEY_FAILURE.equals(jobStatus)) {
				status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to upload application bits", null));
				return status;
			}

			/* delete the tmp file */
			appPackage.delete();
			return status;

		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			status.add(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
			return status;
		}
	}
}
