/*******************************************************************************
 * Copyright (c) 2012, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.tasks;

import java.util.Date;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class TaskJob extends Job implements ITaskCanceller {

	private String userRunningTask;
	private boolean keep;
	private String finalMessage = "Done";
	private TaskInfo task;
	private IStatus realResult;
	private Long taskExpirationTime = null;

	private static int ActiveInstanceCount = 0;

	public TaskJob(String userRunningTask, boolean keep) {
		super("Long running task job");
		this.userRunningTask = userRunningTask;
		this.keep = keep;
	}

	public static int GetActiveCount() {
		return ActiveInstanceCount;
	}

	protected void setFinalMessage(String message) {
		this.finalMessage = message;
	}
	
	protected void setTaskExpirationTime(Long taskExpirationTime){
		this.taskExpirationTime = taskExpirationTime;
	}

	public JSONObject getFinalResult() throws JSONException {
		JSONObject finalResult = new JSONObject();
		finalResult.put(ServerStatus.PROP_MESSAGE, finalMessage);
		return finalResult;
	}

	public IStatus getRealResult() {
		return realResult;
	}

	private ITaskService getTaskService() {
		return Activator.getDefault().getTaskService();
	}

	private synchronized void cleanUp() {
		if (task != null && task.isRunning() == true) {
			setTaskResult(getRealResult() == null ? new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Task finished with unknown status.") : getRealResult());
		}
	}

	public synchronized TaskInfo startTask() {
		task = getTaskService().createTask(userRunningTask, keep, this);
		if (getRealResult() != null) {
			setTaskResult(getRealResult());
		}
		return task;
	}
	
	public synchronized void removeTask(){
		if(task!=null){
			try {
				getTaskService().removeTask(task.getUserId(), task.getId(), task.isKeep());
			} catch (TaskOperationException e) {
				LogHelper.log(e);
			}
		}
	}
	
	public synchronized void setTaskLoaded(int loaded) {
		if (task == null) return;
		task.setLoaded(loaded);
		getTaskService().updateTask(task);
	}
	
	public synchronized void setTaskTotal(int total) {
		if (task == null) return;
		task.setTotal(total);
		getTaskService().updateTask(task);
	}
	
	public synchronized void setTaskMessage(String msg) {
		if (task == null) return;
		task.setMessage(msg);
		getTaskService().updateTask(task);
	}

	@Deprecated
	protected IStatus performJob() {
		return Status.CANCEL_STATUS;
	}
	
	/**
	 * Perform a task with a progress monitor.  This method should be
	 * overridden, not extended.
	 * 
	 * @param monitor The main progress monitor for the job.
	 * @return the appropriate IStatus.
	 */
	protected IStatus performJob(IProgressMonitor monitor) {
		return performJob();
	}

	private synchronized void setTaskResult(IStatus result) {
		if(!task.isRunning()){
			return;
		}
		this.realResult = result;
		task.done(getRealResult());
		if(taskExpirationTime!=null){
			task.setExpires(new Date().getTime() + taskExpirationTime);
		}
		getTaskService().updateTask(task);
	}

	@Override
	protected IStatus run(IProgressMonitor progressMonitor) {
		ActiveInstanceCount++;
		try {
			realResult = performJob(progressMonitor);
			if (task == null) {
				return Status.OK_STATUS; // see bug 353190;;
			}
			setTaskResult(realResult);
			//return the actual result so errors are logged, see bug 353190
			return Status.OK_STATUS; // see bug 353190;
		} finally {
			ActiveInstanceCount--;
			cleanUp();
		}
	}

	public boolean cancelTask() {
		this.cancel();
		return true;
	}

	@Override
	protected void canceling() {
		super.canceling();
		if (task != null && task.isRunning()) {
			task.done(new Status(IStatus.CANCEL, ServerConstants.PI_SERVER_CORE, "Task was canceled."));
			if(taskExpirationTime!=null){
				task.setExpires(new Date().getTime() + taskExpirationTime);
			}
			getTaskService().updateTask(task);
		}
		cleanUp();
	}

}
