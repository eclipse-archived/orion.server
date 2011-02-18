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

import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GitWebResourceDecorator implements IWebResourceDecorator {

	@Override
	public void addAtributesFor(HttpServletRequest request, URI resource,
			JSONObject representation) {
		IPath targetPath = new Path(resource.getPath());
		if (targetPath.segmentCount() <= 1)
			return;
		String servlet = targetPath.segment(0);
		if (!"file".equals(servlet) && !"workspace".equals(servlet))
			return;

		boolean isWorkspace = ("workspace".equals(servlet));

		try {
			// assumption that Git resources may live only under another Git
			// resource
			if (GitUtils.getGitDir(targetPath, request.getRemoteUser()) != null) {
				addGitLinks(resource, representation, isWorkspace);

				JSONArray children = representation
						.optJSONArray(ProtocolConstants.KEY_CHILDREN);
				if (children != null) {
					for (int i = 0; i < children.length(); i++) {
						JSONObject child = children.getJSONObject(i);
						if (child.getBoolean(ProtocolConstants.KEY_DIRECTORY)) {
							addGitLinks(resource, child, false);
						}
					}
				}
			}
		} catch (Exception e) {
			// log and continue
			LogHelper.log(e);
		}
	}

	private void addGitLinks(URI resource, JSONObject representation,
			boolean isWorkspace) throws URISyntaxException, JSONException {

		URI location = null;

		if (isWorkspace
				&& representation.has(ProtocolConstants.KEY_CONTENT_LOCATION))
			location = new URI(
					representation
							.getString(ProtocolConstants.KEY_CONTENT_LOCATION));
		if (!isWorkspace)
			location = new URI(
					representation.getString(ProtocolConstants.KEY_LOCATION));
		if (location == null)
			return;

		IPath targetPath = new Path(location.getPath());

		// add Git Diff URI
		IPath path = new Path(GitServlet.GIT_URI + '/'
				+ GitConstants.DIFF_RESOURCE).append(targetPath);
		URI link = new URI(resource.getScheme(), resource.getAuthority(),
				path.toString(), null, null);
		representation.put(GitConstants.KEY_DIFF, link.toString());

		// add Git Status URI
		path = new Path(GitServlet.GIT_URI + '/' + GitConstants.STATUS_RESOURCE)
				.append(targetPath);
		link = new URI(resource.getScheme(), resource.getAuthority(),
				path.toString(), null, null);
		representation.put(GitConstants.KEY_STATUS, link.toString());

		// add Git Index URI
		path = new Path(GitServlet.GIT_URI + '/' + GitConstants.INDEX_RESOURCE)
				.append(targetPath);
		link = new URI(resource.getScheme(), resource.getAuthority(),
				path.toString(), null, null);
		representation.put(GitConstants.KEY_INDEX, link.toString());
	}
}
