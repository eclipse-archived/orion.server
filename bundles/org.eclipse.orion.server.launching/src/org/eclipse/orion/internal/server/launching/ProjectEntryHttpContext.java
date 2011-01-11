/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.launching;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.osgi.service.http.HttpContext;

public class ProjectEntryHttpContext implements HttpContext {

	private static class ProjectInfo {
		private IProject project;
		private IPath[] roots;

		public ProjectInfo(IProject project, IPath[] roots) {
			this.project = project;
			this.roots = roots;
		}

		public IProject getProject() {
			return project;
		}

		public IPath[] getRoots() {
			return roots;
		}
	}

	private Map<String /*projectName*/, ProjectInfo> projects = new HashMap<String, ProjectInfo>();

	public ProjectEntryHttpContext(IProject project) {
		this.projects.put(project.getName(), new ProjectInfo(project, null));
	}

	public void addProject(IProject project) {
		projects.put(project.getName(), new ProjectInfo(project, null));
	}

	public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
		// TODO Auto-generated method stub
		return true;
	}

	public URL getResource(String name) {
		IFile file = getMappedResource(new Path(name));
		if (file != null && file.exists()) {
			try {
				return file.getLocationURI().toURL();
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// TODO Auto-generated method stub
		return null;
	}

	public String getMimeType(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	public String getHostedPath(IPath path) {
		if (path == null)
			return ""; //$NON-NLS-1$
		String projectName = path.segment(0);
		path = path.removeFirstSegments(1);

		if (projects.containsKey(projectName)) {
			ProjectInfo info = projects.get(projectName);
			IPath[] roots = info.getRoots();
			if (roots != null) {
				for (int i = 0; i < roots.length; i++) {
					if (path.matchingFirstSegments(roots[i]) == roots[i].segmentCount()) {
						return path.removeFirstSegments(roots[i].segmentCount()).toString();
					}

				}
			} else {
				IFile file = info.getProject().getFile(path);
				if (file.exists())
					return file.getProjectRelativePath().toString();
			}
		}
		return null;
	}

	private IFile getMappedResource(IPath path) {
		for (String projectName : projects.keySet()) {
			ProjectInfo info = projects.get(projectName);
			IProject project = info.getProject();
			IPath[] projectRoots = info.getRoots();
			if (projectRoots != null) {
				for (int i = 0; i < projectRoots.length; i++) {
					IFile mappedResource = project.getFile(projectRoots[i].append(path));
					if (mappedResource.exists())
						return mappedResource;
				}
			} else {
				IFile mappedResource = info.getProject().getFile(path);
				if (mappedResource.exists())
					return mappedResource;
			}
		}
		return null;
	}

}
