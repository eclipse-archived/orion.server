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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.commands.GetLogsCommand;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.objects.Log;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogsHandlerV1 extends AbstractRESTHandler<Log> {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	public LogsHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected Log buildResource(HttpServletRequest request, String pathString) throws CoreException {

		if (pathString == null) {
			return null;
		}

		IPath path = new Path(pathString);
		return new Log(path.segment(0), path.segment(1));
	}

	@Override
	protected CFJob handleGet(final Log resource, HttpServletRequest request, HttpServletResponse response, final String path) {

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					Target target = CFActivator.getDefault().getTargetMap().getTarget(userId);
					if (target == null) {
						String msg = "Target not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					if (target.getSpace() == null) {
						String msg = "Space not set"; //$NON-NLS-1$
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}

					if (resource.getApplication() != null) {
						return new GetLogsCommand(target, resource.getApplication(), resource.getName(), this.requestLocation).doIt();
					}
					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "No application name", null);

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
