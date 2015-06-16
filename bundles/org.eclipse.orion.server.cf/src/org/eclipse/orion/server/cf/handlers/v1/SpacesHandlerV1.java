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

import java.net.URI;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.objects.Space;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.cf.utils.HttpUtil;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpacesHandlerV1 extends AbstractRESTHandler<Space> {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	public SpacesHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected Space buildResource(HttpServletRequest request, String pathString) throws CoreException {
		return null;
	}

	@Override
	protected CFJob handleGet(Space space, HttpServletRequest request, HttpServletResponse response, final String pathString) {
		final JSONObject targetJSON = extractJSONData(IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_TARGET));

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					URL targetUrl = null;
					if (targetJSON != null) {
						try {
							targetUrl = new URL(targetJSON.getString(CFProtocolConstants.KEY_URL));
						} catch (Exception e) {
							// do nothing
						}
					}

					Target target = CFActivator.getDefault().getTargetRegistry().getTarget(userId, targetUrl);
					if (target == null) {
						return HttpUtil.createErrorStatus(IStatus.WARNING, "CF-TargetNotSet", "Target not set");
					}

					IPath path = new Path(pathString);
					final String spaceId = path.segment(0);

					/* get space */
					URI targetURI = URIUtil.toURI(target.getUrl());
					URI orgsURI = targetURI.resolve("/v2/spaces/" + spaceId);

					GetMethod getDomainsMethod = new GetMethod(orgsURI.toString());
					ServerStatus confStatus = HttpUtil.configureHttpMethod(getDomainsMethod, target.getCloud());
					if (!confStatus.isOK())
						return confStatus;
					
					getDomainsMethod.setQueryString("inline-relations-depth=1"); //$NON-NLS-1$

					ServerStatus status = HttpUtil.executeMethod(getDomainsMethod);
					if (!status.isOK())
						return status;

					Space space = new Space().setCFJSON(status.getJsonData());
					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, space.toJSON());
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
