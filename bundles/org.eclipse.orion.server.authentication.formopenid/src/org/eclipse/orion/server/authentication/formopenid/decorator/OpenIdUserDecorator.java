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
package org.eclipse.orion.server.authentication.formopenid.decorator;

import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.core.LogHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenIdUserDecorator implements IWebResourceDecorator {

	private boolean decorate = false;

	public void addAtributesFor(HttpServletRequest request, URI resource, JSONObject representation) {
		if (!decorate) {
			return;
		}

		if (!"/users".equals(request.getServletPath()))
			return;

		try {
			addPluginLinks(request, resource, representation);

			JSONArray children = representation.optJSONArray("users");
			if (children != null) {
				for (int i = 0; i < children.length(); i++) {
					JSONObject child = children.getJSONObject(i);
					addPluginLinks(request, resource, child);
				}
			}
		} catch (Exception e) {
			// log and continue
			LogHelper.log(e);
		}
	}

	private void addPluginLinks(HttpServletRequest request, URI resource, JSONObject representation) throws URISyntaxException, JSONException {
		JSONArray plugins = representation.optJSONArray("Plugins");
		if (plugins != null) {
			JSONObject plugin = new JSONObject();
			URI result = new URI(resource.getScheme(), resource.getUserInfo(), resource.getHost(), resource.getPort(), request.getContextPath() + "/mixloginstatic/userProfilePlugin.html", null, null); //$NON-NLS-1$
			plugin.put("Url", result);
			plugins.put(plugin);
		}
	}

	public void setDecorate(boolean decorate) {
		this.decorate = decorate;
	}
}
