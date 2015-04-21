/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others 
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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.AnalyzerException;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.ParserException;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseManifestJSONCommand implements ICFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private JSONObject manifestJSON;
	private ManifestParseTree manifest;

	private IFileStore appStore;
	private String contentLocation;
	private String commandName;
	private String userId;

	private IFileStore persistBaseLocation;

	public IFileStore getPersistBaseLocation() {
		return persistBaseLocation;
	}

	public ManifestParseTree getManifest() {
		return manifest;
	}

	public IFileStore getAppStore() {
		return appStore;
	}

	public ParseManifestJSONCommand(JSONObject manifestJSON, String userId, String contentLocation) {
		this.manifestJSON = manifestJSON;
		this.userId = userId;
		this.contentLocation = contentLocation;
		this.commandName = "Parse manifest JSON representation";
	}

	/* checks whether the given path may be access by the user */
	private ServerStatus canAccess(IPath contentPath) throws CoreException {
		String accessLocation = "/file/" + contentPath.toString(); //$NON-NLS-1$
		if (contentPath.segmentCount() < 1)
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Forbidden access to application contents", null);

		if (!AuthorizationService.checkRights(userId, accessLocation, "GET")) //$NON-NLS-1$
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Forbidden access to application contents", null);
		else
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);
	}

	@Override
	public IStatus doIt() {

		if (manifestJSON == null)
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, //
					"Missing manifest JSON representation", null);
		try {

			/* get the contentLocation file store */
			IPath contentPath = new Path(contentLocation).removeFirstSegments(1);
			ServerStatus accessStatus = canAccess(contentPath);
			if (!accessStatus.isOK())
				return accessStatus;

			IFileStore fileStore = NewFileServlet.getFileStore(null, contentPath);
			if (!fileStore.fetchInfo().isDirectory()) {
				fileStore = fileStore.getParent();
				contentPath = contentPath.removeLastSegments(1);
			}

			/* parse the manifest */
			manifest = ManifestUtils.parse(manifestJSON);

			/* optional */
			ManifestParseTree app = manifest.get("applications").get(0); //$NON-NLS-1$
			ManifestParseTree pathNode = app.getOpt(CFProtocolConstants.V2_KEY_PATH);
			String path = (pathNode != null) ? pathNode.getValue() : ""; //$NON-NLS-1$

			if (path.isEmpty())
				path = "."; //$NON-NLS-1$

			IPath appStorePath = contentPath.append(path);
			accessStatus = canAccess(appStorePath);
			if (!accessStatus.isOK())
				return accessStatus;

			appStore = NewFileServlet.getFileStore(null, appStorePath);

			if (appStore == null) {
				String msg = NLS.bind("Failed to find application content due to incorrect path parameter: {0}", appStorePath);
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
			}

			/* store the persist manifest location */
			if (appStore.fetchInfo().isDirectory())
				persistBaseLocation = appStore;
			else
				persistBaseLocation = fileStore;

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);

		} catch (IllegalArgumentException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (ParserException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (AnalyzerException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (InvalidAccessException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
