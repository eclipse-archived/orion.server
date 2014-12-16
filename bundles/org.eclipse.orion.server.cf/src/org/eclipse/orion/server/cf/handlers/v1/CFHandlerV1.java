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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.ds.objects.Plan;
import org.eclipse.orion.server.cf.objects.*;

/**
 * A REST handler for Orion CF API v 1.0.
 */
public class CFHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<String> targetHandlerV1;
	private ServletResourceHandler<String> infoHandlerV1;
	private ServletResourceHandler<String> appsHandlerV1;
	private ServletResourceHandler<String> orgsHandlerV1;
	private ServletResourceHandler<String> spacesHandlerV1;
	private ServletResourceHandler<String> routesHandlerV1;
	private ServletResourceHandler<String> domainsHandlerV1;
	private ServletResourceHandler<String> manifestsHandlerV1;
	private ServletResourceHandler<String> servicesHandlerV1;
	private ServletResourceHandler<String> plansHandlerV1;
	private ServletResourceHandler<String> loggregatorHandlerV1;

	public CFHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		targetHandlerV1 = new TargetHandlerV1(statusHandler);
		infoHandlerV1 = new InfoHandlerV1(statusHandler);
		appsHandlerV1 = new AppsHandlerV1(statusHandler);
		orgsHandlerV1 = new OrgsHandlerV1(statusHandler);
		spacesHandlerV1 = new SpacesHandlerV1(statusHandler);
		routesHandlerV1 = new RoutesHandlerV1(statusHandler);
		domainsHandlerV1 = new DomainsHandlerV1(statusHandler);
		manifestsHandlerV1 = new ManifestsHandlerV1(statusHandler);
		servicesHandlerV1 = new ServicesHandlerV1(statusHandler);
		plansHandlerV1 = new PlansHandlerV1(statusHandler);
		loggregatorHandlerV1 = new LoggregatorHandlerV1(statusHandler);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String cFPathInfo) throws ServletException {
		String[] infoParts = cFPathInfo.split("\\/", 3); //$NON-NLS-1$

		String pathString = null;
		if (infoParts.length >= 3) {
			pathString = infoParts[2];
		}
		if (request.getContextPath().length() != 0) {
			IPath path = pathString == null ? Path.EMPTY : new Path(pathString);
			IPath contextPath = new Path(request.getContextPath());
			if (contextPath.isPrefixOf(path)) {
				pathString = path.removeFirstSegments(contextPath.segmentCount()).toString();
			}
		}

		if (infoParts[1].equals(Target.RESOURCE)) {
			return targetHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Info.RESOURCE)) {
			return infoHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(App.RESOURCE)) {
			return appsHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Org.RESOURCE)) {
			return orgsHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Route.RESOURCE)) {
			return routesHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Domain.RESOURCE)) {
			return domainsHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Space.RESOURCE)) {
			return spacesHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Manifest.RESOURCE)) {
			return manifestsHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Service.RESOURCE)) {
			return servicesHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals(Plan.RESOURCE)) {
			return plansHandlerV1.handleRequest(request, response, pathString);
		} else if (infoParts[1].equals("logz")) {
			return loggregatorHandlerV1.handleRequest(request, response, pathString);
		}

		return false;
	}
}
