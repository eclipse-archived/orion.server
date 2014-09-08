/*******************************************************************************
 * Copyright (c) 2010, 2013 IBM Corporation and others.
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
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles serialization of {@link ProjectInfo} objects.
 */
public class ProjectInfoResourceHandler extends MetadataInfoResourceHandler<ProjectInfo> {

	public ProjectInfoResourceHandler() {
		super();
	}

	public static JSONObject toJSON(WorkspaceInfo workspace, ProjectInfo project, URI parentLocation) {
		JSONObject result = MetadataInfoResourceHandler.toJSON(project);
		try {
			result.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(parentLocation, "project/" + project.getFullName())); //$NON-NLS-1$
			URI base = parentLocation.resolve(""); //$NON-NLS-1$
			result.put(ProtocolConstants.KEY_CONTENT_LOCATION, WorkspaceResourceHandler.computeProjectURI(base, workspace, project));
		} catch (JSONException e) {
			//can't happen because key and value are well-formed
		}
		return result;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, ProjectInfo project) throws ServletException {
		return false;
	}

}
