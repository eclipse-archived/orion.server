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
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.json.*;

/**
 * Augments a file resource with information about parents up to the level
 * of the project. 
 */
public class ProjectParentDecorator implements IWebResourceDecorator {

	public ProjectParentDecorator() {
		super();
	}

	/*(non-Javadoc)
	 * @see org.eclipse.orion.internal.server.core.IWebResourceDecorator#addAtributesFor(java.net.URI, org.json.JSONObject)
	 */
	public void addAtributesFor(HttpServletRequest request, URI resource, JSONObject representation) {
		IPath resourcePath = new Path(resource.getPath());

		if (!"/file".equals(request.getServletPath())) //$NON-NLS-1$
			return;
		try {
			if (resourcePath.hasTrailingSeparator() && !representation.getBoolean(ProtocolConstants.KEY_DIRECTORY)) {
				resourcePath = resourcePath.append(representation.getString(ProtocolConstants.KEY_NAME));
			}
			addParents(resource, representation, resourcePath);
			//set the name of the project file to be the project name
			if (resourcePath.segmentCount() == 3) {
				WebWorkspace workspace = WebWorkspace.fromId(resourcePath.segment(1));
				WebProject project = workspace.getProjectByName(resourcePath.segment(2));
				String projectName = project.getName();
				if (projectName != null)
					representation.put(ProtocolConstants.KEY_NAME, projectName);
			}
		} catch (JSONException e) {
			//Shouldn't happen because names and locations should be valid JSON.
			//Since we are just decorating some else's response we shouldn't cause a failure
			LogHelper.log(e);
		}
	}

	private void addParents(URI resource, JSONObject representation, IPath resourcePath) throws JSONException {
		//start at parent of current resource
		resourcePath = resourcePath.removeLastSegments(1).addTrailingSeparator();
		JSONArray parents = new JSONArray();
		//for all but the project we can just manipulate the path to get the name and location
		while (resourcePath.segmentCount() > 3) {
			try {
				URI uri = resource.resolve(new URI(null, resourcePath.toString(), null));
				addParent(parents, resourcePath.lastSegment(), new URI(null, null, null, -1, uri.getPath(), uri.getQuery(), uri.getFragment()));
			} catch (URISyntaxException e) {
				//ignore this parent
				LogHelper.log(e);
			}
			resourcePath = resourcePath.removeLastSegments(1);
		}
		//add the project
		if (resourcePath.segmentCount() == 3) {
			WebWorkspace workspace = WebWorkspace.fromId(resourcePath.segment(1));
			WebProject project = workspace.getProjectByName(resourcePath.segment(2));
			try {
				URI uri = resource.resolve(new URI(null, resourcePath.toString(), null));
				addParent(parents, project.getName(), new URI(null, null, null, -1, uri.getPath(), uri.getQuery(), uri.getFragment()));
			} catch (URISyntaxException e) {
				//ignore this project
				LogHelper.log(e);
			}
		}
		representation.put(ProtocolConstants.KEY_PARENTS, parents);
	}

	/**
	 * Adds a parent resource representation to the parent array
	 */
	private void addParent(JSONArray parents, String name, URI location) throws JSONException {
		JSONObject parent = new JSONObject();
		parent.put(ProtocolConstants.KEY_NAME, name);
		parent.put(ProtocolConstants.KEY_LOCATION, location);
		URI childLocation;
		try {
			childLocation = new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), location.getPath(), "depth=1", location.getFragment()); //$NON-NLS-1$
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		parent.put(ProtocolConstants.KEY_CHILDREN_LOCATION, childLocation);
		parents.put(parent);
	}
}
