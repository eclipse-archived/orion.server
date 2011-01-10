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

package org.eclipse.e4.internal.webide.launching;

import java.util.*;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class WebLaunchConfiguration extends LaunchConfigurationDelegate {

	public static final String LAUNCH_CONFIG_TYPE = "org.eclipse.e4.webide.launching.WebLaunchConfiguration"; //$NON-NLS-1$

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.
	 * eclipse.debug.core.ILaunchConfiguration, java.lang.String,
	 * org.eclipse.debug.core.ILaunch,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
		//		IWorkspace workspace = WorkspaceUtil.constructWorkspace(configuration.getAttribute("workspaceId", (String) null)); //$NON-NLS-1$
		IWorkspace workspace = getWorkspace();
		String alias = configuration.getAttribute("hostedRoot", (String) null); //$NON-NLS-1$

		HttpService service = Activator.getHttpService();
		if (service != null) {
			@SuppressWarnings("unchecked")
			Map<String, String> projectMap = configuration.getAttribute("projects", Collections.EMPTY_MAP); //$NON-NLS-1$
			Map<String, ProjectEntryHttpContext> contextMap = new HashMap<String, ProjectEntryHttpContext>();
			for (String projectName : projectMap.keySet()) {
				IProject project = workspace.getRoot().getProject(projectName);
				String projectAlias = projectMap.get(projectName);
				String contextAlias = projectAlias.length() > 0 ? alias + '/' + projectAlias : alias;

				if (contextMap.containsKey(contextAlias)) {
					contextMap.get(contextAlias).addProject(project);
				} else {
					ProjectEntryHttpContext context = new ProjectEntryHttpContext(project);
					contextMap.put(contextAlias, context);
					register(service, contextAlias, context);
				}
			}

			WebProcess process = new WebProcess(launch, contextMap);
			process.setAttribute(WebProcess.WEB_PROCESS_ALIAS, alias);
			launch.addProcess(process);
		}
	}

	private IWorkspace getWorkspace() {
		return null;
	}

	private void register(HttpService service, String alias, ProjectEntryHttpContext projectContext) {
		try {
			service.registerResources(alias, "/", projectContext); //$NON-NLS-1$
		} catch (NamespaceException e) {
			service.unregister(alias);
			try {
				service.registerResources(alias, "/", projectContext); //$NON-NLS-1$
			} catch (NamespaceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}
}
