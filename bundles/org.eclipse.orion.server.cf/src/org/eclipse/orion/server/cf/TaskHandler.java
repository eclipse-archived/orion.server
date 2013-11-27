/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.servlets.CFServlet;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.core.tasks.TaskJob;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Workaround for Defect 90007
 */
public class TaskHandler {

	/**
	 * A constant used to determine if an operation is short enough to return 
	 * the result immediately (OK, 200) rather than wait for the task to finish 
	 * (Accepted, 202).
	 */
	public static final long WAIT_TIME = 100;

	private static URI createTaskLocation(URI baseLocation, String taskId, boolean keep) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getAuthority(), (keep ? "/task/id/" : "/task/temp/") + taskId, null, null); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public static final String getUserId(HttpServletRequest req) {
		if (req.getRemoteUser() != null) {
			return req.getRemoteUser();
		}
		return req.getSession(true).getId();
	}

	/**
	 * Schedules {@link TaskJob} and handles response. If job lasts more than {@link this#WAIT_TIME}
	 * handler starts a task and returns 202 (Accepted) response with task details. If job finishes sooner the
	 * response if immediately returned filled with {@link TaskJob#getResult()}, if result is OK, than only
	 * {@link ServerStatus#getJsonData()} is returned, if result is OK and it is not an instance of {@link ServerStatus}
	 * than {@link TaskJob#getFinalResult()} is returned as a response content.
	 * 
	 * @param request
	 * @param response
	 * @param job Job that should be handled as a task.
	 * @param statusHandler status handler to handle error statuses.
	 * @return <code>true</code> if job was handled properly.
	 * @throws IOException
	 * @throws ServletException
	 * @throws URISyntaxException
	 * @throws JSONException
	 */
	public static boolean handleTaskJob(HttpServletRequest request, HttpServletResponse response, TaskJob job, ServletResourceHandler<IStatus> statusHandler) throws IOException, ServletException, URISyntaxException, JSONException {
		job.schedule();

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
					jobIsDone.wait(WAIT_TIME);
				}
			}
		} catch (InterruptedException e) {
			// ignore
		}
		job.removeJobChangeListener(jobListener);

		if (job.getState() == Job.NONE || job.getRealResult() != null) {
			return writeResult(request, response, job, statusHandler);
		}
		TaskInfo task = job.startTask();
		JSONObject result = task.toJSON();
		URI taskLocation = createTaskLocation(ServletResourceHandler.getURI(request), task.getId(), task.isKeep());
		result.put(ProtocolConstants.KEY_LOCATION, taskLocation);
		if (!task.isRunning()) {
			job.removeTask(); // Task is not used, we may remove it
			return writeResult(request, response, job, statusHandler);
		}
		response.setHeader(ProtocolConstants.HEADER_LOCATION, ServletResourceHandler.resovleOrionURI(request, taskLocation).toString());
		OrionServlet.writeJSONResponse(request, response, result);
		response.setStatus(HttpServletResponse.SC_ACCEPTED);
		return true;
	}

	private static boolean writeResult(HttpServletRequest request, HttpServletResponse response, TaskJob job, ServletResourceHandler<IStatus> statusHandler) throws ServletException, IOException, JSONException {
		IStatus result = job.getRealResult();
		if (!result.isOK()) {
			return statusHandler.handleRequest(request, response, result);
		}
		if (result instanceof ServerStatus && ((ServerStatus) result).getJsonData() != null) {
			ServerStatus status = (ServerStatus) result;
			CFServlet.writeJSONResponse(request, response, status.getJsonData());
			return true;
		}
		CFServlet.writeJSONResponse(request, response, job.getFinalResult());
		return true;
	}
}
