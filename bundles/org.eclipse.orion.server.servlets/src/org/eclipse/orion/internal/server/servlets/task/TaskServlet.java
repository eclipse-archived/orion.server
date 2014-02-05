/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskDoesNotExistException;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.core.tasks.TaskInfo.TaskStatus;
import org.eclipse.orion.server.core.tasks.TaskOperationException;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.EMPTY : new Path(pathInfo);
		if (path.segmentCount() != 2) {
			handleException(resp, "Invalid request path: " + path, null, HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		try {
			JSONObject data = OrionServlet.readJSONRequest(req);
			if (!"true".equals(data.getString(TaskStatus.ABORT.toString()))) {
				handleException(resp, "Invalid request paramethers, try {" + TaskStatus.ABORT.toString() + ":true}", null, HttpServletResponse.SC_BAD_REQUEST);
			}
		} catch (JSONException e1) {
			handleException(resp, "Invalid request paramethers, try {" + TaskStatus.ABORT.toString() + ":true}", e1, HttpServletResponse.SC_BAD_REQUEST);
		}

		boolean isKeep = "id".equals(path.segment(0));
		String taskId = path.segment(1);
		ITaskService taskService = taskTracker.getService();
		try {
			taskService.cancelTask(TaskJobHandler.getUserId(req), taskId, isKeep);
		} catch (TaskDoesNotExistException e) {
			handleException(resp, "Could not cancel task that does not exist: " + e.getTaskId(), e, HttpServletResponse.SC_NOT_FOUND);
			return;
		} catch (TaskOperationException e) {
			handleException(resp, e.getMessage(), e);
			return;
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.EMPTY : new Path(pathInfo);
		ITaskService taskService = taskTracker.getService();
		if (path.segmentCount() == 0) {
			taskService.removeCompletedTasks(TaskJobHandler.getUserId(req));
			List<TaskInfo> tasks = taskService.getTasks(TaskJobHandler.getUserId(req));
			List<String> locations = new ArrayList<String>();
			try {
				for (TaskInfo task : tasks) {
					locations.add(getJsonWithLocation(req, task).optString(ProtocolConstants.KEY_LOCATION));
				}
			} catch (Exception e) {
				handleException(resp, e.getMessage(), e);
			}
			writeJSONResponse(req, resp, new JSONArray(locations));
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

	public JSONObject getTasksList(Collection<TaskInfo> tasks, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, JSONException, URISyntaxException {
		return getTasksList(tasks, new ArrayList<String>(), req, resp);
	}

	private static JSONObject getJsonWithLocation(HttpServletRequest req, TaskInfo task) throws JSONException, URISyntaxException {
		return getJsonWithLocation(req, task, true);
	}

	private static JSONObject getJsonWithLocation(HttpServletRequest req, TaskInfo task, boolean includeResult) throws JSONException, URISyntaxException {
		JSONObject taskJson = includeResult ? task.toJSON() : task.toLightJSON();
		if (taskJson.optString(ProtocolConstants.KEY_LOCATION, "").equals("")) {
			URI uri = ServletResourceHandler.getURI(req);
			if (task.isKeep()) {
				taskJson.put(ProtocolConstants.KEY_LOCATION, new URI(uri.getScheme(), null, "/task/id/" + task.getId(), null, null));
			} else {
				taskJson.put(ProtocolConstants.KEY_LOCATION, new URI(uri.getScheme(), null, "/task/temp/" + task.getId(), null, null));
			}
		}
		return taskJson;
	}

	public JSONObject getTasksList(Collection<TaskInfo> tasks, Collection<String> deletedTasks, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, JSONException, URISyntaxException {
		JSONObject result = new JSONObject();
		JSONArray tasksList = new JSONArray();
		for (TaskInfo task : tasks) {
			tasksList.put(getJsonWithLocation(req, task, "true".equals(req.getParameter("results"))));
		}
		result.put(ProtocolConstants.KEY_DELETED_CHILDREN, deletedTasks);
		result.put(ProtocolConstants.KEY_CHILDREN, tasksList);
		return result;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		String pathInfo = req.getPathInfo();
		IPath path = pathInfo == null ? Path.EMPTY : new Path(pathInfo);
		ITaskService taskService = taskTracker.getService();

		if (path.segmentCount() == 0) {

			List<TaskInfo> tasks = taskService.getTasks(TaskJobHandler.getUserId(req));
			try {
				writeJSONResponse(req, resp, getTasksList(tasks, req, resp));
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
			JSONObject errorDescription = new JSONObject();
			try {
				errorDescription.put("taskNotFound", taskId);
				handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Task not found: " + taskId, errorDescription, null));
				//				handleException(resp, "Task not found: " + taskId, null, HttpServletResponse.SC_NOT_FOUND);
			} catch (JSONException e) {
				handleException(resp, e.getMessage(), e);
			}
			return;
		}
		JSONObject result = task.toJSON();
		if (task.isKeep()) {
			try {
				if (result.optString(ProtocolConstants.KEY_LOCATION, "").equals(""))
					result.put(ProtocolConstants.KEY_LOCATION, ServletResourceHandler.getURI(req));
			} catch (JSONException e) {
				handleException(resp, e.getMessage(), e);
			}
		}
		writeJSONResponse(req, resp, result);
	}

	public static void writeJSONResponse(HttpServletRequest req, HttpServletResponse resp, Object result) throws IOException {
		if (result instanceof JSONObject) {
			JSONObject jsonResult = (JSONObject) result;
			if (jsonResult.optBoolean("LocationOnly")) {
				writeJSONResponse(req, resp, result, JsonURIUnqualificationStrategy.LOCATION_ONLY);
				return;
			}
		}
		writeJSONResponse(req, resp, result, JsonURIUnqualificationStrategy.ALL);
	}
}
