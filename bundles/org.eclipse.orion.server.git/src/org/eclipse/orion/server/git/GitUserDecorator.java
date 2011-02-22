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
package org.eclipse.orion.server.git;

import java.net.URI;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.core.LogHelper;
import org.json.JSONArray;
import org.json.JSONObject;

public class GitUserDecorator implements IWebResourceDecorator {

	@Override
	public void addAtributesFor(HttpServletRequest request, URI resource,
			JSONObject representation) {
		IPath targetPath = new Path(resource.getPath());
		if (targetPath.segmentCount() <= 1)
			return;
		String servlet = targetPath.segment(0);
		if (!"users".equals(servlet))
			return;

		try {
			JSONArray children = representation.optJSONArray("Plugins");
			if (children != null) {
				JSONObject plugin = new JSONObject();
				URI result = new URI(resource.getScheme(),
						resource.getUserInfo(), resource.getHost(),
						resource.getPort(),
						"/plugins/git/userProfilePlugin.html", null, null); //$NON-NLS-1$
				plugin.put("Url", result);
				children.put(plugin);
			}
		} catch (Exception e) {
			// log and continue
			LogHelper.log(e);
		}
	}
}
