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

import java.net.URL;
import java.net.URLDecoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFActivator;
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

		if (pathString == null) {
			return null;
		}

		IPath path = new Path(pathString);
		return new Log(path.segment(0), path.segment(1));
	}

	@Override
	protected CFJob handleGet(final Log resource, HttpServletRequest request, HttpServletResponse response, final String path) {
		final String targetStr = IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_TARGET);
		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {

					Target target = null;
					if (targetStr != null) {
						JSONObject targetJSON = new JSONObject(URLDecoder.decode(targetStr, "UTF8"));
						URL targetUrl = new URL(targetJSON.getString(CFProtocolConstants.KEY_URL));

						target = CFActivator.getDefault().getTargetRegistry().getTarget(userId, targetUrl);
						if (target == null) {
							return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Target not set", null);
						}

						IStatus result = new SetOrgCommand(target, targetJSON.optString("Org")).doIt();
						if (!result.isOK())
							return result;

						result = new SetSpaceCommand(target, targetJSON.optString("Space")).doIt();
						if (!result.isOK())
							return result;
					} else {
						target = CFActivator.getDefault().getTargetRegistry().getTarget(userId);
					}

					if (target == null) {
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Target not set", null);
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
