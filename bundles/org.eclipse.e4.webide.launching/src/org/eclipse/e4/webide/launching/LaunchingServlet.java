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
package org.eclipse.e4.webide.launching;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.debug.core.*;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.e4.internal.webide.launching.WebLaunchConfiguration;
import org.eclipse.e4.internal.webide.launching.WebProcess;
import org.eclipse.e4.webide.server.servlets.EclipseWebServlet;
import org.json.*;

public class LaunchingServlet extends EclipseWebServlet {
	private static final long serialVersionUID = -6570910445863789260L;

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String path = req.getPathInfo();

		if (path == null || path.equals("/")) { //$NON-NLS-1$
			String workspace = req.getParameter("workspace"); //$NON-NLS-1$
			//return a list of the available launch configurations
			try {
				ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
				ILaunchConfigurationType type = manager.getLaunchConfigurationType(WebLaunchConfiguration.LAUNCH_CONFIG_TYPE);
				ILaunchConfiguration[] configs = manager.getLaunchConfigurations(type);

				JSONArray result = new JSONArray();
				for (int i = 0; i < configs.length; i++) {
					if (configs[i].getAttribute("workspaceId", "").equals(workspace)) { //$NON-NLS-1$ //$NON-NLS-2$
						JSONObject config = new JSONObject();
						config.put("name", configs[i].getName()); //$NON-NLS-1$
						config.put("memento", configs[i].getMemento()); //$NON-NLS-1$
						config.put("isRunning", getRunningProcess(configs[i]) != null); //$NON-NLS-1$
						result.put(config);
					}
				}
				writeJSONResponse(req, resp, result);
				return;
			} catch (Exception e) {
				handleException(resp, "Error retrieving launch configurations", e);
				return;
			}
		}
		super.doGet(req, resp);
	}

	private IProcess getRunningProcess(ILaunchConfiguration config) {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		IProcess[] processes = manager.getProcesses();
		for (int i = 0; i < processes.length; i++) {
			if (processes[i].getLaunch().getLaunchConfiguration().equals(config)) {
				return processes[i];
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathinfo = req.getPathInfo();
		String launchConfig = req.getParameter("config"); //$NON-NLS-1$
		String newConfiguration = req.getParameter("launchConfig"); //$NON-NLS-1$
		String workspaceName = req.getParameter("workspaceId"); //$NON-NLS-1$

		IPath wsPath = null;
		if (pathinfo != null) {
			int index = pathinfo.indexOf("/at/"); //$NON-NLS-1$
			if (index > -1)
				wsPath = new Path(pathinfo.substring(index + 4));
		}

		try {
			if (launchConfig != null) {
				ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
				ILaunchConfiguration launchConfiguration = manager.getLaunchConfiguration(launchConfig);
				JSONObject result = launch(launchConfiguration, wsPath, resp);
				if (result != null)
					writeJSONResponse(req, resp, result);
			} else if (newConfiguration != null) {
				JSONObject configInfo = new JSONObject(newConfiguration);
				JSONObject result = newLaunch(configInfo, resp);
				if (result != null)
					writeJSONResponse(req, resp, result);
			} else {
				launch(workspaceName, wsPath, resp);
			}
		} catch (JSONException e) {
			handleException(resp, "Malformed configuration information.", e);
			return;
		} catch (CoreException e) {
			handleException(resp, "Error creating launch configuration", e);
			return;
		}
	}

	private JSONObject newLaunch(JSONObject configInfo, HttpServletResponse response) throws CoreException, JSONException {
		String configName = configInfo.getString("name"); //$NON-NLS-1$
		if (configName.length() == 0)
			return null;

		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType launchConfigType = launchManager.getLaunchConfigurationType(WebLaunchConfiguration.LAUNCH_CONFIG_TYPE);

		ILaunchConfigurationWorkingCopy newLaunch = launchConfigType.newInstance(null, configName);
		newLaunch.setAttribute("hostedRoot", "/" + configInfo.getString("alias")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		newLaunch.setAttribute("workspaceId", configInfo.getString("workspaceId")); //$NON-NLS-1$ //$NON-NLS-2$
		newLaunch.setAttribute("initialPage", configInfo.getString("initialPage")); //$NON-NLS-1$ //$NON-NLS-2$

		Map<String, String> projectMap = new HashMap<String, String>();
		JSONArray projects = configInfo.getJSONArray("projects"); //$NON-NLS-1$
		for (int i = 0; i < projects.length(); i++) {
			JSONObject projectEntry = (JSONObject) projects.get(i);
			String projectName = (String) projectEntry.get("project"); //$NON-NLS-1$
			String subAlias = (String) projectEntry.get("subAlias"); //$NON-NLS-1$
			projectMap.put(projectName, subAlias);
		}
		newLaunch.setAttribute("projects", projectMap); //$NON-NLS-1$

		ILaunchConfiguration config = newLaunch.doSave();
		JSONObject result = new JSONObject();
		result.put("memento", config.getMemento()); //$NON-NLS-1$
		result.put("name", config.getName()); //$NON-NLS-1$
		return result;
	}

	private void launch(String workspaceName, IPath path, HttpServletResponse response) throws CoreException, JSONException {
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType launchConfigType = launchManager.getLaunchConfigurationType(WebLaunchConfiguration.LAUNCH_CONFIG_TYPE);

		ILaunchConfigurationWorkingCopy newInstance = launchConfigType.newInstance(null, "hosted"); //$NON-NLS-1$
		newInstance.setAttribute("workspaceId", workspaceName); //$NON-NLS-1$
		newInstance.setAttribute("hostedRoot", "/hosted"); //$NON-NLS-1$ //$NON-NLS-2$
		if (path != null) {
			Map<String, String> projectMap = new HashMap<String, String>();
			projectMap.put(path.segment(0), ""); //$NON-NLS-1$
			newInstance.setAttribute("projects", projectMap); //$NON-NLS-1$
			newInstance.setAttribute("wsPath", path.toString()); //$NON-NLS-1$
		}
		ILaunchConfiguration config = newInstance.doSave();
		launch(config, path, response);
	}

	private JSONObject launch(ILaunchConfiguration config, IPath path, HttpServletResponse response) throws CoreException, JSONException {
		IProcess process = getRunningProcess(config);
		if (process != null) {
			process.terminate();
			JSONObject result = new JSONObject();
			result.put("name", config.getName()); //$NON-NLS-1$
			result.put("isRunning", false); //$NON-NLS-1$
			result.put("status", "Stopped [" + config.getName() + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return result;
		}

		ILaunch launch = config.launch(ILaunchManager.RUN_MODE, new NullProgressMonitor());
		IProcess[] processes = launch.getProcesses();
		if (processes == null || processes.length == 0) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return null;
		}

		WebProcess webProcess = (WebProcess) processes[0];
		String initialPage = config.getAttribute("initialPage", (String) null); //$NON-NLS-1$
		String fullPath = webProcess.getAttribute(WebProcess.WEB_PROCESS_ALIAS) + '/' + initialPage;

		JSONObject result = new JSONObject();//"Launched [" + data + "]"
		result.put("name", config.getName()); //$NON-NLS-1$
		result.put("initialPage", fullPath); //$NON-NLS-1$
		result.put("isRunning", true); //$NON-NLS-1$
		result.put("status", "Launched [" + config.getName() + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return result;

	}

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
	}
}
