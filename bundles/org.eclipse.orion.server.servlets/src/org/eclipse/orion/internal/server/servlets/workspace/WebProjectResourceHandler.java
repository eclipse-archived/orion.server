/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles serialization of {@link WebElement} objects.
 */
public class WebProjectResourceHandler extends WebElementResourceHandler<WebProject> {
	public WebProjectResourceHandler() {
		super();
	}

	public static JSONObject toJSON(WebWorkspace workspace, WebProject project, URI parentLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(project);
		try {
			result.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(parentLocation, "project/" + project.getId())); //$NON-NLS-1$
			URI base = parentLocation.resolve(""); //$NON-NLS-1$
			result.put(ProtocolConstants.KEY_CONTENT_LOCATION, WorkspaceResourceHandler.computeProjectURI(base, workspace, project));
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
