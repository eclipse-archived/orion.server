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
package org.eclipse.orion.server.cf.handlers.v1;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.commands.ParseManifestCommand;
import org.eclipse.orion.server.cf.ds.IDeploymentService;
import org.eclipse.orion.server.cf.ds.objects.Plan;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ApplicationReconstructor;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlansHandlerV1 extends AbstractRESTHandler<Plan> {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	public PlansHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected Plan buildResource(HttpServletRequest request, String path) throws CoreException {
		return null;
	}

	@Override
	protected CFJob handleGet(Plan pathPlan, HttpServletRequest request, HttpServletResponse response, final String path) {

		return new CFJob(request, false) {

			@Override
			protected IStatus performJob() {

				try {

					IPath contentPath = new Path(path.startsWith("/") ? path : "/" + path); //$NON-NLS-1$ //$NON-NLS-2$
					if (!AuthorizationService.checkRights(userId, contentPath.toString(), "GET")) //$NON-NLS-1$ //$NON-NLS-2$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, "Forbidden access to application contents", null);

					IFileStore contentLocation = NewFileServlet.getFileStore(null, contentPath.removeFirstSegments(1));
					if (!contentLocation.fetchInfo().isDirectory())
						contentLocation = contentLocation.getParent();

					/* check if the application has a manifest */
					ManifestParseTree manifest = null;
					ParseManifestCommand parseManifestCommand = new ParseManifestCommand(null, userId, contentPath.toString()); /* TODO: set target */
					parseManifestCommand.setApplicationAnalyzer(new ApplicationReconstructor());

					IStatus status = parseManifestCommand.doIt();
					if (status.isOK())
						manifest = parseManifestCommand.getManifest();

					IDeploymentService deploymentService = CFActivator.getDefault().getDeploymentService();
					List<Plan> plans = deploymentService.getDeploymentPlans(contentLocation, manifest);

					JSONArray children = new JSONArray();
					for (Plan plan : plans)
						children.put(plan.toJSON());

					JSONObject resp = new JSONObject();
					resp.put(ProtocolConstants.KEY_CHILDREN, children);
					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, resp);

				} catch (Exception ex) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex);
					logger.error(msg, ex);
					return status;
				}
			}

		};
	}
}
