/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.WebElementResourceHandler;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles serialization of {@link WebClone} objects.
 */
public class WebCloneResourceHandler extends WebElementResourceHandler<WebClone> {

	public WebCloneResourceHandler() {
		super();
	}

	public static JSONObject toJSON(WebClone clone, URI parentLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(clone);
		try {
			result.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(parentLocation, clone.getId()));
			result.put(ProtocolConstants.KEY_CONTENT_LOCATION, clone.getContentLocation().toString());
		} catch (JSONException e) {
			// can't happen because key and value are well-formed
		}
		return result;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, WebClone object) throws ServletException {
		return false;
	}

}
