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
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GitWebResourceDecorator implements IWebResourceDecorator {

	@Override
	public void addAtributesFor(URI resource, JSONObject representation) {
		IPath targetPath = new Path(resource.getPath());
		if (targetPath.segmentCount() <= 1)
			return;
		String servlet = targetPath.segment(0);
		if (!"file".equals(servlet))
			return;
		try {
			//TODO: we need to check if the resource is a Git resource
			if ("file".equals(servlet))
				addGitLinks(resource, representation);
			JSONArray children = representation.optJSONArray(ProtocolConstants.KEY_CHILDREN);
			if (children != null) {
				for (int i = 0; i < children.length(); i++) {
					JSONObject child = children.getJSONObject(i);
					if (child.getBoolean(ProtocolConstants.KEY_DIRECTORY)) {
						addGitLinks(resource, child);
					}
				}
			}
		} catch (Exception e) {
			//log and continue
			LogHelper.log(e);
		}
	}
	
	private void addGitLinks(URI resource, JSONObject representation) throws URISyntaxException, JSONException {
		URI location = new URI(representation.getString(ProtocolConstants.KEY_LOCATION));
		IPath targetPath = new Path(location.getPath());
		
		// add Git Diff URI
		IPath path = new Path("/git/diff").append(targetPath);
		URI link = new URI(resource.getScheme(), resource.getAuthority(), path.toString(), null, null);
		representation.put("GitDiff", link.toString());
		
		// add Git Status URI
		path = new Path("/git/status").append(targetPath);
		link = new URI(resource.getScheme(), resource.getAuthority(), path.toString(), null, null);
		representation.put("GitResult", link.toString());
	}
}
