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

import java.io.IOException;
import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.*;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseManifestCommand extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	private String userId;
	private String contentLocation;
	private String commandName;

	private ManifestParseTree manifest;
	private IFileStore appStore;
	private IFileStore manifestStore;

	private Analyzer applicationAnalyzer;
	private boolean strict;

	public ParseManifestCommand(Target target, String userId, String contentLocation) {
		super(target);
		this.userId = userId;
		this.contentLocation = contentLocation;
		this.applicationAnalyzer = null;
		this.strict = false;
		this.commandName = NLS.bind("Parse application manifest: {0}", contentLocation);
	}

	public void setStrict(boolean force) {
		this.strict = force;
	}

	public void setApplicationAnalyzer(Analyzer applicationAnalyzer) {
		this.applicationAnalyzer = applicationAnalyzer;
	}

	public ManifestParseTree getManifest() {
		return manifest;
	}

	public IFileStore getAppStore() {
		return appStore;
	}

	public IFileStore getManifestStore() {
		return manifestStore;
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

	private ServerStatus cannotFindManifest(IPath contentPath) {
		// Note: we are assuming the content path is inside a project folder
		IPath manifestPath = contentPath.removeFirstSegments(2).append(ManifestConstants.MANIFEST_FILE_NAME);
		String msg = "Failed to find /" + manifestPath + ". If the manifest is in a different folder, please select the manifest file or folder before deploying.";
		return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
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
			if (!fileStore.fetchInfo().isDirectory() && !strict) {
				fileStore = fileStore.getParent();
				contentPath = contentPath.removeLastSegments(1);
			}

			if (fileStore == null)
				return cannotFindManifest(contentPath);

			/* lookup the manifest description */
			manifestStore = strict ? fileStore : fileStore.getChild(ManifestConstants.MANIFEST_FILE_NAME);
			if (!manifestStore.fetchInfo().exists())
				return cannotFindManifest(contentPath);

			/* parse within the project sandbox */
			ProjectInfo project = OrionConfiguration.getMetaStore().readProject(contentPath.segment(0), contentPath.segment(1));
			IFileStore projectStore = NewFileServlet.getFileStore(null, project);

			/* parse the manifest */
			ManifestParseTree manifest = null;
			String targetBase = null;

			if (target != null) {
				URI targetURI = URIUtil.toURI(target.getUrl());
				targetBase = targetURI.getHost().substring(4);
			}

			manifest = ManifestUtils.parse(projectStore, manifestStore, targetBase, applicationAnalyzer);
			ManifestParseTree app = manifest.get("applications").get(0); //$NON-NLS-1$

			/* optional */
			ManifestParseTree pathNode = app.getOpt(CFProtocolConstants.V2_KEY_PATH);
			String path = (pathNode != null) ? pathNode.getValue() : ""; //$NON-NLS-1$

			if (path.isEmpty())
				path = "."; //$NON-NLS-1$

			try {

				IPath appStorePath = contentPath.append(path);
				accessStatus = canAccess(appStorePath);
				if (!accessStatus.isOK())
					return accessStatus;

				this.appStore = NewFileServlet.getFileStore(null, appStorePath);

				if (appStore == null) {
					String msg = NLS.bind("Failed to find application content due to incorrect path parameter: {0}", appStorePath);
					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
				}

				this.manifest = manifest;

			} catch (Exception ex) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Failed to locate application contents as specified in the manifest.", null);
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);

		} catch (TokenizerException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e.getDetails(), null);
		} catch (ParserException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e.getDetails(), null);
		} catch (AnalyzerException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), e.getDetails(), null);
		} catch (InvalidAccessException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (IOException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
