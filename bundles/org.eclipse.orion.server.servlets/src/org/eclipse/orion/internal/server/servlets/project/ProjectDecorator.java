/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.project;

import java.net.URI;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.project.Project;
import org.json.JSONObject;

public class ProjectDecorator implements IWebResourceDecorator {

	public void addAtributesFor(HttpServletRequest request, URI resource, JSONObject representation) {
		if (!"/file".equals(request.getServletPath())) //$NON-NLS-1$
			return;
		String pathInfo = request.getPathInfo();
		Path path = new Path(pathInfo);
		if (path.segmentCount() < 2) {
			return;
		}
		try {
			ProjectInfo project = OrionConfiguration.getMetaStore().readProject(path.segment(0), path.segment(1));
			Project projectData = Project.fromProjectInfo(project);
			if (projectData.exists()) {
				JSONObject projectJson = new JSONObject(); //TODO decide what project information should be included in decorator
				projectJson.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(URIUtil.append(resource.resolve("/project/"), path.segment(0)), path.segment(1)).toString());
				representation.put(ProtocolConstants.KEY_PROJECT_INFO, projectJson);
			}
		} catch (Exception e) {
			LogHelper.log(e);
		}
	}
}
