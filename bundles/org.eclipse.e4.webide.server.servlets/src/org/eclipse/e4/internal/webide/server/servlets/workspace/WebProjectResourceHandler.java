/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.internal.webide.server.servlets.workspace;

import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.e4.internal.webide.server.IWebResourceDecorator;
import org.eclipse.e4.internal.webide.server.servlets.ProtocolConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/**
 * Handles serialization of {@link WebElement} objects.
 */
public class WebProjectResourceHandler extends WebElementResourceHandler<WebProject> {
	public WebProjectResourceHandler() {
		super();
		//not the ideal location because we have no lifecycle for removal
		registerParentDecorator();
	}

	/**
	 * Registers a decorator that augments file data with parent path information up
	 * to the level of the project.
	 */
	private void registerParentDecorator() {
		BundleContext bc = FrameworkUtil.getBundle(WebProjectResourceHandler.class).getBundleContext();
		bc.registerService(IWebResourceDecorator.class, new ProjectParentDecorator(), null);
	}

	public static JSONObject toJSON(WebProject project, URI parentLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(project);
		try {
			result.put(ProtocolConstants.KEY_LOCATION, WorkspaceResourceHandler.computeProjectContentLocation(parentLocation, project));
		} catch (JSONException e) {
			//can't happen because key and value are well-formed
		}
		return result;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, WebProject object) throws ServletException {
		return false;
	}

}
