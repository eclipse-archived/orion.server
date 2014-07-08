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

import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.manifest.v2.*;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.service.DeploymentDescription;
import org.eclipse.orion.server.cf.service.IDeploymentService;
import org.eclipse.orion.server.core.ServerStatus;

public class GetDeploymentDescriptionCommand extends AbstractCFCommand {

	private String userId;
	private String contentLocation;
	private String applicationName;
	private ManifestParseTree manifest;
	private String applicationType;

	public GetDeploymentDescriptionCommand(Target target, String userId, String applicationName, String contentLocation) {
		super(target);

		this.contentLocation = contentLocation;
		this.applicationName = applicationName;
		this.userId = userId;
	}

	public ManifestParseTree getManifest() {
		return manifest;
	}

	/**
	 * 
	 * @return
	 */
	public String getApplicationType() {
		return applicationType;
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
	protected ServerStatus _doIt() {
		try {

			/* get the contentLocation application store */
			IPath contentPath = new Path(contentLocation).removeFirstSegments(1);
			ServerStatus accessStatus = canAccess(contentPath);
			if (!accessStatus.isOK())
				return accessStatus;

			/* note we're assuming a trivial path property, i.e. 'path: .' */
			IFileStore applicationStore = NewFileServlet.getFileStore(null, contentPath);
			if (!applicationStore.fetchInfo().isDirectory())
				applicationStore = applicationStore.getParent();

			if (applicationStore == null)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Forbidden access to application contents", null);

			/* dynamically deduce the deployment description using the deployment service */
			IDeploymentService deploymentService = CFActivator.getDefault().getDeploymentService();
			DeploymentDescription deploymentDescription = deploymentService.getDeploymentDescription(applicationName, applicationStore);

			if (deploymentDescription == null)
				/* admit failure - the deployment service could not deduce the deployment description  */
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Could not create a deployment description", null);

			/* set the manifest and application type */
			manifest = ManifestUtils.parse(deploymentDescription);
			applicationType = deploymentDescription.getApplicationType();

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);

		} catch (TokenizerException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (ParserException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (AnalyzerException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (Exception ex) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Invalid deployment description", null);
		}
	}
}
