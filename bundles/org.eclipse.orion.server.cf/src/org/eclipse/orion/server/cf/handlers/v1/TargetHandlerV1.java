/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
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
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFActivator;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

public class TargetHandlerV1 extends AbstractRESTHandler<Target> {

	//	private final Logger logger = LoggerFactory.getLogger("com.ibm.cf.orion.server"); //$NON-NLS-1$

	public TargetHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected Target buildResource(HttpServletRequest request, String path) throws CoreException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected CFJob handleList(HttpServletRequest request, HttpServletResponse response, final String path) {
		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					JSONObject result = new JSONObject();
					Target target = CFActivator.getDefault().getTargetMap().getTarget(this.userId);
					if (target != null)
						result.append(ProtocolConstants.KEY_CHILDREN, target.toJSON());

					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
				} catch (JSONException e) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					//					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	@Override
	protected CFJob handlePost(Target resource, HttpServletRequest request, HttpServletResponse response, final String path) {
		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {

				//				try {
				//					String user = req.getRemoteUser();
				//					JSONObject o = OrionServlet.readJSONRequest(req);
				//					String newUrl = o.getString("URL");
				//					OAuth2AccessToken token = null; //o.optString("token", null);
				//					if (newUrl != null) {
				//						newUrl = newUrl.trim();
				//					}			
				//					URL targetUrl = null;
				//					try {
				//						targetUrl = new URL(newUrl);
				//					} catch (MalformedURLException e) {
				//						return statusHandler.handleRequest(req, resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "The target URL is invalid", e));				
				//					}
				//					URL currentUrl = null; 
				//					Target target = null;
				//					target = CFService.getTarget(user);
				//					currentUrl = target.getURL();							
				//					
				//					if (newUrl == null || newUrl.length() == 0) {
				//						CFService.removeCFClient(req.getRemoteUser());
				//						target.setUser(null);
				//						target.setURL(null);
				//						Activator.getDefault().getTargetMap().putTarget(user, target);
				//					} else {
				//						CFService.removeCFClient(req.getRemoteUser());
				//						target.setUser(null);
				//						target.setURL(targetUrl);
				//						Activator.getDefault().getTargetMap().putTarget(user, target);
				//						try {
				//							getCFService(req);									
				//						} catch (CFException e) {
				//							// Retain the original values when there's a connection issue
				//							target.setURL(currentUrl);
				//							Activator.getDefault().getTargetMap().putTarget(user, target);
				//							throw e;
				//						}
				//					}
				//
				//					o = new JSONObject().put("target", newUrl);
				//					OrionServlet.writeJSONResponse(req, resp, o);
				//					
				//				} catch (Exception e) {
				//					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
				//					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
				//					logger.error(msg, e);
				//					return status;
				//				}

				return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		};
	}
}
