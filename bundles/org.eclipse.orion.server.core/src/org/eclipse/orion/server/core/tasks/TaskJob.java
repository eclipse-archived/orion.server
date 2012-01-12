/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.tasks;

import java.net.URI;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.orion.server.core.ServerConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public abstract class TaskJob extends Job implements ITaskCanceler {

	private String userRunningTask;
	private boolean isIdempotent;
	private boolean canCancel;
	private String message;
	private String finalMessage = "Done";
	private URI finalLocation = null;
	private TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;

	public TaskJob(String taskName, String userRunningTask, String initialMessage, boolean isIdempotent, boolean canCancel) {
		super(taskName);
		this.userRunningTask = userRunningTask;
		this.isIdempotent = isIdempotent;
		this.canCancel = canCancel;
		this.message = initialMessage;
	}

	public TaskJob(String userRunningTask, String taskName, String initialMessage) {
		this(userRunningTask, taskName, initialMessage, false, false);
	}

	public TaskJob(String userRunningTask, String taskName) {
		this(userRunningTask, taskName, "", false, false);
	}

	protected void setFinalMessage(String message) {
		this.finalMessage = message;
	}

	protected void setFinalLocation(URI location) {
		this.finalLocation = location;
	}

	public JSONObject getFinalResult() throws JSONException {
		JSONObject finalResult = new JSONObject();
		if (finalLocation != null) {
			finalResult.put(TaskInfo.KEY_LOCATION, finalLocation);
		}
		finalResult.put(TaskInfo.KEY_MESSAGE, finalMessage == null ? message : finalMessage);
		return finalResult;
	}

	ITaskService getTaskService() {
		if (taskService == null) {
			BundleContext context = Activator.getDefault().getContext();
			if (taskServiceRef == null) {
				taskServiceRef = context.getServiceReference(ITaskService.class);
				if (taskServiceRef == null)
					throw new IllegalStateException("Task service not available");
			}
			taskService = context.getService(taskServiceRef);
			if (taskService == null)
				throw new IllegalStateException("Task service not available");
		}
		return taskService;
	}

	private synchronized void cleanUp() {
		if (task != null && task.isRunning() == true) {
			task.done(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Task finished with unknown status."));
			getTaskService().updateTask(task);
		}
		taskService = null;
		if (taskServiceRef != null) {
			Activator.getDefault().getContext().ungetService(taskServiceRef);
			taskServiceRef = null;
		}
	}

	protected void setMessage(String message) {
		this.message = message;
		if (task != null) {
			task.setMessage(message);
			getTaskService().updateTask(task);
		}
	}

	public synchronized TaskInfo startTask() {
		if (canCancel) {
			task = getTaskService().createTask(getName(), userRunningTask, this, isIdempotent);
		} else {
			task = getTaskService().createTask(getName(), userRunningTask, isIdempotent);
		}
		if (message != null && message.length() > 0) {
			task.setMessage(message);
			getTaskService().updateTask(task);
		}
		return task;
	}

	protected abstract IStatus performJob();

	@Override
	protected IStatus run(IProgressMonitor progressMonitor) {
		try {
			IStatus result = performJob();
			if (task == null) {
				return result;
			}
			if (result.isOK()) {
				if (finalLocation != null)
					task.setResultLocation(finalLocation);
				task.done(result);
				// set the message after updating the task with the result
				task.setMessage(finalMessage);
			} else {
				task.done(result);
			}
			getTaskService().updateTask(task);
			//return the actual result so errors are logged, see bug 353190
			return Status.OK_STATUS; // see bug 353190;
		} finally {
			cleanUp();
		}
	}

	public void cancelTask() {
		this.cancel();
	}

	@Override
	protected void canceling() {
		super.canceling();
		if (task != null && task.isRunning()) {
			task.done(new Status(IStatus.CANCEL, ServerConstants.PI_SERVER_CORE, "Task was canceled."));
			getTaskService().updateTask(task);
		}
		cleanUp();
	}

}
