/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.handlers.v1;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.commands.*;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TargetHandlerV1 extends AbstractRESTHandler<Target> {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	public TargetHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected Target buildResource(HttpServletRequest request, String path) throws CoreException {
		return null;
	}

	@Override
	protected CFJob handleGet(Target target, HttpServletRequest request, HttpServletResponse response, final String path) {
		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					Target target = CFActivator.getDefault().getTargetRegistry().getTarget(this.userId);
					if (target == null) {
						String msg = "Target not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, target.toJSON());
				} catch (JSONException e) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	@Override
	protected CFJob handlePost(Target resource, HttpServletRequest request, HttpServletResponse response, final String path) {
		final JSONObject jsonData = extractJSONData(request);

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					ComputeTargetCommand computeTarget = new ComputeTargetCommand(this.userId, jsonData);
					IStatus result = computeTarget.doIt();
					Target target = computeTarget.getTarget();

					if (jsonData.has("Username") && jsonData.has("Password")) {
						result = new LoginCommand(target, jsonData.getString("Username"), jsonData.getString("Password")).doIt();
						if (!result.isOK())
							return result;
					}

					CFActivator.getDefault().getTargetRegistry().markDefault(this.userId, target);

					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, target.toJSON());
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
	protected CFJob handleDelete(Target resource, HttpServletRequest request, HttpServletResponse response, final String path) {
		//final JSONObject jsonData = extractJSONData(request);

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					Target target = CFActivator.getDefault().getTargetRegistry().getTarget(this.userId);
					if (target == null) {
						String msg = "Target not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					LogoutCommand logoutCommand = new LogoutCommand(target);
					return logoutCommand.doIt();
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

}
