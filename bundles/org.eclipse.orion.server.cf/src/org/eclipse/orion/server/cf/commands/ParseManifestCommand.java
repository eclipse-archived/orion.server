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
package org.eclipse.orion.server.cf.commands;

import java.net.URI;
import java.util.Scanner;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.*;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseManifestCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$
	private String commandName;

	private App application;
	private String userId;
	private String contentLocation;

	public ParseManifestCommand(Target target, App app, String userId, String contentLocation) {
		super(target);
		this.application = app;
		this.userId = userId;
		this.contentLocation = contentLocation;
		this.commandName = NLS.bind("Parse application manifest: {0}", contentLocation);
	}

	/* checks whether the given path may be access by the user */
	private ServerStatus canAccess(IPath contentPath) throws CoreException {
		String accessLocation = "/file/" + contentPath.toString();
		if (contentPath.segmentCount() < 1)
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Forbidden access to application contents", null);

		if (!AuthorizationService.checkRights(userId, accessLocation, "GET"))
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Forbidden access to application contents", null);
		else
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}

	@Override
	protected ServerStatus _doIt() {
		try {

			/* get the contentLocation file store */
			IPath contentPath = new Path(contentLocation).removeFirstSegments(1);
			ServerStatus accessStatus = canAccess(contentPath);
			if (!accessStatus.isOK())
				return accessStatus;

			IFileStore fileStore = NewFileServlet.getFileStore(null, contentPath);
			if (!fileStore.fetchInfo().isDirectory()) {
				fileStore = fileStore.getParent();
			}

			if (fileStore == null) {
				String msg = "Failed to find application manifest. If you have one, please select it or the folder that contains it before deploying.";
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
			}

			/* lookup the manifest description */
			IFileStore manifestStore = fileStore.getChild(ManifestUtils.MANIFEST_FILE_NAME);
			if (!manifestStore.fetchInfo().exists()) {
				String msg = "Failed to find application manifest. If you have one, please select it or the folder that contains it before deploying.";
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
			}

			/* parse the manifest token tree */
			Scanner manifestScanner = new Scanner(manifestStore.openInputStream(EFS.NONE, null));
			ManifestNode manifestTree = ManifestParser.parse(manifestScanner);
			manifestScanner.close();

			/* parse the manifest */
			URI targetURI = URIUtil.toURI(target.getUrl());
			JSONObject manifest = manifestTree.toJSON(targetURI);

			/* set application store relative to the path parameter */
			JSONObject appJSON = ManifestUtils.getApplication(manifest);

			String path = appJSON.optString(CFProtocolConstants.V2_KEY_PATH); /* optional */
			if (path.isEmpty())
				path = "."; //$NON-NLS-1$

			try {

				IPath appStorePath = contentPath.append(path);
				accessStatus = canAccess(appStorePath);
				if (!accessStatus.isOK())
					return accessStatus;

				IFileStore appStore = NewFileServlet.getFileStore(null, appStorePath);

				if (appStore == null) {
					String msg = NLS.bind("Failed to find application content due to incorrect path parameter: {0}", appStorePath);
					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
				}

				application.setAppStore(appStore);
				application.setManifest(manifest);

			} catch (Exception ex) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Failed to locate application contents as specified in the manifest.", null);
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);

		} catch (ParseException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
