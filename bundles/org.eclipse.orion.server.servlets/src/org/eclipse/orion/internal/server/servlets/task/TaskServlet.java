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
package org.eclipse.orion.internal.server.servlets.task;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A service for the client to obtain information about the current state of long
 * running operations on the server.
 */
public class TaskServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	ServiceTracker<ITaskService, ITaskService> taskTracker;

	public TaskServlet() {
		initTaskService();
	}

	private void initTaskService() {
		taskTracker = new ServiceTracker<ITaskService, ITaskService>(Activator.bundleContext, ITaskService.class, null);
		taskTracker.open();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.EMPTY : new Path(pathInfo);
		if (path.segmentCount() != 2 || !"id".equals(path.segment(0))) {//$NON-NLS-1$
			handleException(resp, "Invalid request path: " + path, null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		ITaskService taskService = taskTracker.getService();
		if (taskService == null) {
			handleException(resp, "Task service is unavailable", null);
			return;
		}
		String taskId = path.segment(1);
		TaskInfo task = taskService.getTask(taskId);
		if (task == null) {
			handleException(resp, "Task not found: " + taskId, null, HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		JSONObject result = task.toJSON();
		try {
			if (result.optString(ProtocolConstants.KEY_LOCATION, "").isEmpty())
				result.put(ProtocolConstants.KEY_LOCATION, getURI(req).toString());
		} catch (JSONException e) {
			//cannot happen
		}
		writeJSONResponse(req, resp, result);
	}
}
