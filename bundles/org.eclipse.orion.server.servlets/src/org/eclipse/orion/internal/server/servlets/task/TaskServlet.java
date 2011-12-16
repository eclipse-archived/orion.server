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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.resources.UniversalUniqueIdentifier;
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
	TaskNonotificationRegistry notificationRegistry;

	public TaskServlet() {
		initTaskService();
	}

	private void initTaskService() {
		taskTracker = new ServiceTracker<ITaskService, ITaskService>(Activator.bundleContext, ITaskService.class, null);
		taskTracker.open();
		notificationRegistry = new TaskNonotificationRegistry(this, taskTracker.getService());
	}

	public static final String getUserId(HttpServletRequest req) {
		if (req.getRemoteUser() != null) {
			return req.getRemoteUser();
		} else {
			return req.getSession(true).getId();
		}
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
			taskService.removeCompletedTasks(getUserId(req));
			return;
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
				TaskInfo task = taskService.getTask(getUserId(req), taskId);
				if (task == null) {
					handleException(resp, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Task " + taskId + " does not exist", null));
					return;
				}
				taskService.cancelTask(task);
			}
		} catch (JSONException e) {
			handleException(resp, "Could not read request", e);
		} catch (TaskOperationException e) {
			handleException(resp, "Task does not support canceling", e, HttpServletResponse.SC_BAD_REQUEST);
		}

	}

	public JSONObject getTasksList(List<TaskInfo> tasks, Date timestamp, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, JSONException, URISyntaxException {
		JSONObject result = new JSONObject();
		JSONArray tasksList = new JSONArray();
		for (TaskInfo task : tasks) {
			if ("true".equals(req.getParameter("results"))) {
				JSONObject taskJson = task.toJSON();
				if (taskJson.optString(ProtocolConstants.KEY_LOCATION, "").equals(""))
					taskJson.put(ProtocolConstants.KEY_LOCATION, new URI(getURI(req).toString() + "/").resolve("id/" + task.getTaskId()).toString());
				tasksList.put(taskJson);
			} else {
				JSONObject taskJson = task.toLightJSON();
				if (taskJson.optString(ProtocolConstants.KEY_LOCATION, "").equals(""))
					taskJson.put(ProtocolConstants.KEY_LOCATION, new URI(getURI(req).toString() + "/").resolve("id/" + task.getTaskId()).toString());
				tasksList.put(taskJson);
			}

		}
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
			if ("true".equals(req.getParameter(ProtocolConstants.KEY_LONGPOLLING))) {
				if (req.getParameter(ProtocolConstants.KEY_LONGPOLLING_ID) == null) {

					Date modifiedFrom = null;
					Date timestamp = new Date();
					try {
						modifiedFrom = new Date(Long.parseLong(req.getParameter(ProtocolConstants.KEY_LOCAL_TIMESTAMP)));
					} catch (Exception e) {
						//if we can't get timestamp from request than we return all changes
					}

					List<TaskInfo> tasks = taskService.getTasks(getUserId(req), modifiedFrom, runningOnly);
					try {
						JSONObject result = getTasksList(tasks, timestamp, req, resp);
						result.put(ProtocolConstants.KEY_LONGPOLLING_ID, new UniversalUniqueIdentifier().toBase64String());
						resp.setStatus(HttpServletResponse.SC_ACCEPTED);
						writeJSONResponse(req, resp, result);
						notificationRegistry.setLastNotification(result.getString(ProtocolConstants.KEY_LONGPOLLING_ID), timestamp);
						return;
					} catch (JSONException e) {
						handleException(resp, e.getMessage(), e);
						return;
					} catch (URISyntaxException e) {
						handleException(resp, e.getMessage(), e);
						return;
					}
				}
				String longpollingId = req.getParameter(ProtocolConstants.KEY_LONGPOLLING_ID);
				Job job = notificationRegistry.addListener(longpollingId, req, resp, getUserId(req));

				final Object jobIsDone = new Object();
				final JobChangeAdapter jobListener = new JobChangeAdapter() {
					public void done(IJobChangeEvent event) {
						synchronized (jobIsDone) {
							jobIsDone.notify();
						}
					}
				};
				job.addJobChangeListener(jobListener);

				try {
					synchronized (jobIsDone) {
						if (job.getState() != Job.NONE) {
							jobIsDone.wait(LONGPOLLING_WAIT_TIME);
						}
					}
				} catch (InterruptedException e) {
				}
				job.removeJobChangeListener(jobListener);
				if (job.getResult() == null) {
					job.cancel();
				} else {
					if (!job.getResult().isOK()) {
						handleException(resp, job.getResult());
					}
				}
				return;

			}

			Date timestamp = new Date();
			Date modifiedFrom = null;
			try {
				modifiedFrom = new Date(Long.parseLong(req.getParameter(ProtocolConstants.KEY_LOCAL_TIMESTAMP)));
			} catch (Exception e) {
				//if we can't get timestamp from request than we return all changes
			}

			List<TaskInfo> tasks = taskService.getTasks(getUserId(req), modifiedFrom, runningOnly);
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
			handleException(resp, e.getMessage(), e);
		}
		writeJSONResponse(req, resp, result);
	}
}
