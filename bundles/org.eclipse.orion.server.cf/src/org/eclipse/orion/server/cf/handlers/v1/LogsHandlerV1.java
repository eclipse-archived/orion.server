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

import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;

import org.eclipse.orion.server.core.IOUtilities;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.commands.*;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.objects.Log;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogsHandlerV1 extends AbstractRESTHandler<Log> {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	public LogsHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected Log buildResource(HttpServletRequest request, String pathString) throws CoreException {
		return null;
	}

	@Override
	protected CFJob handleGet(final Log log, HttpServletRequest request, HttpServletResponse response, final String pathString) {
		final JSONObject targetJSON = extractJSONData(IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_TARGET));
		IPath path = new Path(pathString);
		final String appName = path.segment(0);
		final String logName = path.segment(1);
		final String instanceNo = path.segment(2);

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					ComputeTargetCommand computeTargetCommand = new ComputeTargetCommand(this.userId, targetJSON);
					IStatus result = computeTargetCommand.doIt();
					if (!result.isOK())
						return result;
					Target target = computeTargetCommand.getTarget();

					if (appName != null && instanceNo != null && logName != null) {
						return new GetLogCommand(target, appName, instanceNo, logName, this.requestLocation).doIt();
					} else if (appName != null) {
						return new GetLogsCommand(target, appName, this.requestLocation).doIt();
					}

					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "No application name", null);

				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", pathString); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

}
