/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
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

	public static final int LONGPOLLING_WAIT_TIME = 60000;
	public static final String KEY_RUNNING_ONLY = "RunningOnly";//$NON-NLS-1$

	ServiceTracker<ITaskService, ITaskService> taskTracker;

	public TaskServlet() {
		initTaskService();
	}

	private void initTaskService() {
		taskTracker = new ServiceTracker<ITaskService, ITaskService>(Activator.bundleContext, ITaskService.class, null);
		taskTracker.open();
	}

	@Override
	protected void handleException(HttpServletResponse resp, String msg, Exception e) throws ServletException {
		super.handleException(resp, msg, e);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.EMPTY : new Path(pathInfo);
		ITaskService taskService = taskTracker.getService();
		if (path.segmentCount() == 0) {
			taskService.removeCompletedTasks(TaskJobHandler.getUserId(req));
			return;
		}

		if (path.segmentCount() != 2 || (!"id".equals(path.segment(0)) && !"temp".equals(path.segment(0)))) {//$NON-NLS-1$
			handleException(resp, "Invalid request path: " + path, null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		boolean isKeep = "id".equals(path.segment(0));

		String taskId = path.segment(1);
		try {
			taskService.removeTask(TaskJobHandler.getUserId(req), taskId, isKeep);
		} catch (TaskDoesNotExistException e) {
			handleException(resp, "Could not remove task that does not exist: " + e.getTaskId(), e, HttpServletResponse.SC_NOT_FOUND);
			return;
		} catch (TaskOperationException e) {
			handleException(resp, e.getMessage(), e);
			return;
		}
	}

	public JSONObject getTasksList(Collection<TaskInfo> tasks, Date timestamp, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, JSONException, URISyntaxException {
		return getTasksList(tasks, new ArrayList<String>(), timestamp, req, resp);
	}

	private static JSONObject getJsonWithLocation(HttpServletRequest req, TaskInfo task) throws JSONException, URISyntaxException {
		return getJsonWithLocation(req, task, true);
	}

	private static JSONObject getJsonWithLocation(HttpServletRequest req, TaskInfo task, boolean includeResult) throws JSONException, URISyntaxException {
		JSONObject taskJson = includeResult ? task.toJSON() : task.toLightJSON();
		if (taskJson.optString(ProtocolConstants.KEY_LOCATION, "").equals("")) {
			if (task.isKeep()) {
				taskJson.put(ProtocolConstants.KEY_LOCATION, new URI(getURI(req).toString() + "/").resolve("id/" + task.getId()).toString());
			} else {
				taskJson.put(ProtocolConstants.KEY_LOCATION, new URI(getURI(req).toString() + "/").resolve("temp/" + task.getId()).toString());
			}
		}
		return taskJson;
	}

	public JSONObject getTasksList(Collection<TaskInfo> tasks, Collection<String> deletedTasks, Date timestamp, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, JSONException, URISyntaxException {
		JSONObject result = new JSONObject();
		JSONArray tasksList = new JSONArray();
		for (TaskInfo task : tasks) {
			tasksList.put(getJsonWithLocation(req, task, "true".equals(req.getParameter("results"))));
		}
		result.put(ProtocolConstants.KEY_DELETED_CHILDREN, deletedTasks);
		result.put(ProtocolConstants.KEY_CHILDREN, tasksList);
		result.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, timestamp.getTime());
		return result;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.EMPTY : new Path(pathInfo);
		ITaskService taskService = taskTracker.getService();

		if (path.segmentCount() == 0) {
			boolean runningOnly = "true".equals(req.getParameter(KEY_RUNNING_ONLY));

			Date timestamp = new Date();

			List<TaskInfo> tasks = taskService.getTasks(TaskJobHandler.getUserId(req));
			try {
				writeJSONResponse(req, resp, getTasksList(tasks, timestamp, req, resp));
			} catch (JSONException e) {
				handleException(resp, e.getMessage(), e);
				return;
			} catch (URISyntaxException e) {
				handleException(resp, e.getMessage(), e);
				return;
			}

			return;
		}

		if (path.segmentCount() != 2 || (!"id".equals(path.segment(0)) && !"temp".equals(path.segment(0)))) {//$NON-NLS-1$
			handleException(resp, "Invalid request path: " + path, null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		if (taskService == null) {
			handleException(resp, "Task service is unavailable", null);
			return;
		}
		String taskId = path.segment(1);
		boolean keep = "id".equals(path.segment(0));
		TaskInfo task = taskService.getTask(TaskJobHandler.getUserId(req), taskId, keep);

		if (task == null) {
			handleException(resp, "Task not found: " + taskId, null, HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		JSONObject result = task.toJSON();
		if (task.isKeep()) {
			try {
				if (result.optString(ProtocolConstants.KEY_LOCATION, "").equals(""))
					result.put(ProtocolConstants.KEY_LOCATION, getURI(req).toString());
			} catch (JSONException e) {
				handleException(resp, e.getMessage(), e);
			}
		}
		writeJSONResponse(req, resp, result);
	}
}
