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
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.tasks.*;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.*;
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

	public static final String getUserId(HttpServletRequest req) {
		if (req.getRemoteUser() != null) {
			return req.getRemoteUser();
		} else {
			return req.getSession(true).getId();
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.EMPTY : new Path(pathInfo);
		ITaskService taskService = taskTracker.getService();
		if (path.segmentCount() == 0) {
			taskService.removeCompletedTasks(getUserId(req));
		}

		if (path.segmentCount() != 2 || !"id".equals(path.segment(0))) {//$NON-NLS-1$
			handleException(resp, "Invalid request path: " + path, null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		String taskId = path.segment(1);
		try {
			taskService.removeTask(getUserId(req), taskId);
		} catch (TaskOperationException e) {
			handleException(resp, e.getMessage(), e);
			return;
		}
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.EMPTY : new Path(pathInfo);
		if (path.segmentCount() != 2 || !"id".equals(path.segment(0))) {//$NON-NLS-1$
			handleException(resp, "Invalid request path: " + path, null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		ITaskService taskService = taskTracker.getService();

		try {
			JSONObject putData = OrionServlet.readJSONRequest(req);
			if (putData.getBoolean("Cancel")) {
				String taskId = path.segment(1);
				taskService.getTask(getUserId(req), taskId).cancelTask();
			}
		} catch (JSONException e) {
			handleException(resp, "Could not read request", e);
		} catch (TaskOperationException e) {
			handleException(resp, "Task does not support canceling", e, HttpServletResponse.SC_BAD_REQUEST);
		}

	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.EMPTY : new Path(pathInfo);
		ITaskService taskService = taskTracker.getService();

		if (path.segmentCount() == 0) {
			List<TaskInfo> tasks = taskService.getTasks(getUserId(req));
			JSONObject result = new JSONObject();
			JSONArray tasksList = new JSONArray();
			for (TaskInfo task : tasks) {
				tasksList.put(task.toJSON());
			}
			try {
				result.put("Children", tasksList);
			} catch (JSONException e) {
				//cannot happen
			}
			writeJSONResponse(req, resp, result);
		}

		if (path.segmentCount() != 2 || !"id".equals(path.segment(0))) {//$NON-NLS-1$
			handleException(resp, "Invalid request path: " + path, null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		if (taskService == null) {
			handleException(resp, "Task service is unavailable", null);
			return;
		}
		String taskId = path.segment(1);
		TaskInfo task = taskService.getTask(getUserId(req), taskId);

		if (task == null) {
			handleException(resp, "Task not found: " + taskId, null, HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		JSONObject result = task.toJSON();
		try {
			if (result.optString(ProtocolConstants.KEY_LOCATION, "").equals(""))
				result.put(ProtocolConstants.KEY_LOCATION, getURI(req).toString());
		} catch (JSONException e) {
			//cannot happen
		}
		writeJSONResponse(req, resp, result);
	}
}
