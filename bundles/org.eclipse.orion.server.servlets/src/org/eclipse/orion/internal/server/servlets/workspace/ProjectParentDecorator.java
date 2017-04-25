/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others.
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

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.file.DirectoryHandlerV1;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.internal.server.servlets.file.ServletFileStoreHandler;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
		if (!"/file".equals(request.getServletPath())) //$NON-NLS-1$
			return;
		try {
			URI base = new URI(resource.getScheme(), resource.getUserInfo(), resource.getHost(), resource.getPort(), request.getServletPath() + "/", null, null);
			IPath basePath = new Path(base.getPath());
			IPath resourcePath = null;
			try {
				String locationString = representation.getString(ProtocolConstants.KEY_LOCATION);
				URI location = new URI(locationString);
				resourcePath = new Path(location.getPath());
			} catch (JSONException je) {
				// no String value found for ProtocolConstants.KEY_LOCATION,
				// use resource path instead
				resourcePath = new Path(resource.getPath());
				if (resourcePath.hasTrailingSeparator() && !representation.getBoolean(ProtocolConstants.KEY_DIRECTORY)) {
					resourcePath = resourcePath.append(representation.getString(ProtocolConstants.KEY_NAME));
				}
			}
			IPath path = resourcePath.makeRelativeTo(basePath);
			if (path.segmentCount() == 1) {
				WorkspaceInfo workspace = OrionConfiguration.getMetaStore().readWorkspace((path.segment(0)));
				String workspaceName = workspace.getFullName();
				if (workspaceName != null)
					representation.put(ProtocolConstants.KEY_NAME, workspaceName);
			}
			//nothing to do if request is not a folder or file
			if (path.segmentCount() < 2)
				return;
			ProjectInfo project = OrionConfiguration.getMetaStore().readProject(path.segment(0), path.segment(1));
			//nothing to do if project does not exist
			if (project == null) {
				return;
			}
			addParents(base, representation, project, path, request, IOUtilities.getQueryParameter(request, "tree"));
			//set the name of the project file to be the project name
			if (path.segmentCount() == 2) {
				String projectName = project.getFullName();
				if (projectName != null)
					representation.put(ProtocolConstants.KEY_NAME, projectName);
			}
		} catch (Exception e) {
			//don't let problems in decorator propagate
			LogHelper.log(e);
		}
	}

	private void addParents(URI resource, JSONObject representation, ProjectInfo project, IPath resourcePath, HttpServletRequest request, String tree) throws JSONException {
		//start at parent of current resource
		resourcePath = resourcePath.removeLastSegments(1).addTrailingSeparator();
		JSONArray parents = new JSONArray();
		//for all but the project we can just manipulate the path to get the name and location
		while (resourcePath.segmentCount() > 2) {
			try {
				URI uri = resource.resolve(new URI(null, null, resourcePath.toString(), null));
				addParent(parents, resourcePath.lastSegment(), new URI(resource.getScheme(), resource.getAuthority(), uri.getPath(), uri.getQuery(), uri.getFragment()), resourcePath, request, tree);
			} catch (URISyntaxException e) {
				//ignore this parent
				LogHelper.log(e);
			}
			resourcePath = resourcePath.removeLastSegments(1);
		}
		//add the project
		if (resourcePath.segmentCount() == 2) {
			try {
				URI uri = resource.resolve(new URI(null, null, resourcePath.toString(), null));
				addParent(parents, project.getFullName(), new URI(resource.getScheme(), resource.getAuthority(), uri.getPath(), uri.getQuery(), uri.getFragment()), resourcePath, request, tree);
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
	private void addParent(JSONArray parents, String name, URI location, IPath resourcePath, HttpServletRequest request, String tree) throws JSONException {
		JSONObject parent;
		if (tree != null) {
			try {
				boolean compressed = "compressed".equals(tree);
				boolean decorated = "decorated".equals(tree);
				IFileStore dir = NewFileServlet.getFileStore(request, resourcePath);
				parent = ServletFileStoreHandler.toJSON(dir, dir.fetchInfo(EFS.NONE, null), compressed ? null : location);
				DirectoryHandlerV1.encodeChildren(dir, location, parent, 1, !compressed);
				if (decorated) OrionServlet.decorateResponse(request, parent, this);
			} catch (CoreException e) {
				return;
			}
		} else {
			parent = new JSONObject();
			parent.put(ProtocolConstants.KEY_NAME, name);
			parent.put(ProtocolConstants.KEY_LOCATION, location);
			URI childLocation;
			try {
				childLocation = new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), location.getPath(), "depth=1", location.getFragment()); //$NON-NLS-1$
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
			parent.put(ProtocolConstants.KEY_CHILDREN_LOCATION, childLocation);
		}
		parents.put(parent);
	}
}
