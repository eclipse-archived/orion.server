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
package org.eclipse.orion.server.core.tasks;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a snapshot of the state of a long running task.
 */
public class TaskInfo {
	private static final String KEY_PERCENT_COMPLETE = "PercentComplete"; //$NON-NLS-1$
	private static final String KEY_ID = "Id"; //$NON-NLS-1$
	private static final String KEY_MESSAGE = "Message"; //$NON-NLS-1$
	private static final String KEY_RUNNING = "Running"; //$NON-NLS-1$
	private final String id;
	private String message = ""; //$NON-NLS-1$
	private int percentComplete = 0;
	private boolean running = false;

	/**
	 * Returns a task object based on its JSON representation. Returns
	 * null if the given string is not a valid JSON task representation.
	 */
	public static TaskInfo fromJSON(String taskString) {
		TaskInfo info;
		try {
			JSONObject json = new JSONObject(taskString);
			info = new TaskInfo(json.getString(KEY_ID));
			info.setMessage(json.optString(KEY_MESSAGE, "")); //$NON-NLS-1$
			info.setRunning(json.optBoolean(KEY_RUNNING, false));
			info.setPercentComplete(json.optInt(KEY_PERCENT_COMPLETE, 0));
			return info;
		} catch (JSONException e) {
			return null;
		}
	}

	public TaskInfo(String id) {
		this.id = id;
	}

	/**
	 * Returns a message describing the current progress state of the task, or the
	 * result if the task is completed.
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @return an integer between 0 and 100 representing the current progress state
	 */
	public int getPercentComplete() {
		return percentComplete;
	}

	public String getTaskId() {
		return id;
	}

	/**
	 * @return whether the task is currently running
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Sets a message describing the current task state. 
	 * @param message the message to set
	 */
	public void setMessage(String message) {
		this.message = message == null ? "" : message; //$NON-NLS-1$
	}

	/**
	 * Sets the percent complete of this task. Values below 0 will be rounded up to zero,
	 * and values above 100 will be rounded down to 100;
	 * @param percentComplete an integer between 0 and 100 representing the
	 * current progress state
	 */
	public void setPercentComplete(int percentComplete) {
		if (percentComplete < 0)
			percentComplete = 0;
		if (percentComplete > 100)
			percentComplete = 100;
		this.percentComplete = percentComplete;
	}

	/**
	 * @param running sets whether the task is currently running
	 */
	public void setRunning(boolean running) {
		this.running = running;
	}

	/**
	 * Returns a JSON representation of this task state.
	 */
	public String toJSON() {
		JSONObject result = new JSONObject();
		try {
			result.put(KEY_RUNNING, isRunning()); 
			result.put(KEY_MESSAGE, getMessage());
			result.put(KEY_ID, getTaskId()); 
			result.put(KEY_PERCENT_COMPLETE, getPercentComplete());
		} catch (JSONException e) {
			//can only happen if key is null
		}
		return result.toString();
	}
}
