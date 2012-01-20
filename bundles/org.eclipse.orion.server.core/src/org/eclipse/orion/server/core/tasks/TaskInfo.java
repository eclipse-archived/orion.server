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

import java.net.URI;
import java.util.Date;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a snapshot of the state of a long running task.
 */
public class TaskInfo {
	public static final String KEY_PERCENT_COMPLETE = "PercentComplete"; //$NON-NLS-1$
	public static final String KEY_ID = "Id"; //$NON-NLS-1$
	public static final String KEY_USER = "User"; //$NON-NLS-1$
	public static final String KEY_MESSAGE = "Message"; //$NON-NLS-1$
	public static final String KEY_RUNNING = "Running"; //$NON-NLS-1$
	public static final String KEY_LOCATION = "Location"; //$NON-NLS-1$
	public static final String KEY_RESULT = "Result"; //$NON-NLS-1$
	public static final String KEY_CAN_BE_CANCELED = "CanBeCanceled"; //$NON-NLS-1$
	public static final String KEY_TIMESTAMP_MODIFIED = "Modified"; //$NON-NLS-1$
	public static final String KEY_TIMESTAMP_CREATED = "Created"; //$NON-NLS-1$
	public static final String KEY_NAME = "Name"; //$NON-NLS-1$
	public static final String KEY_FAILED = "Failed"; //$NON-NLS-1$
	public static final String KEY_CANCELED = "Canceled"; //$NON-NLS-1$
	public static final String KEY_IDEMPOTENT = "Idempotent"; //$NON-NLS-1$
	private final String id;
	private final String userId;
	private boolean idempotent = false;
	private String message = ""; //$NON-NLS-1$
	private int percentComplete = 0;
	private boolean running = true;
	private URI resultLocation = null;
	private IStatus result;
	private boolean canBeCanceled = false;
	private Date modified;
	private Date created;
	private String name;


	/**
	 * Returns a task object based on its JSON representation. Returns
	 * null if the given string is not a valid JSON task representation.
	 * This function does not set <code>canBeCanceled</code>. The caller
	 * must find out himself if the task can be canceled and set this flag.
	 */
	public static TaskInfo fromJSON(String taskString) {
		TaskInfo info;
		try {
			JSONObject json = new JSONObject(taskString);
			info = new TaskInfo(json.getString(KEY_USER), json.getString(KEY_ID), json.optBoolean(KEY_IDEMPOTENT, false));
			info.setMessage(json.optString(KEY_MESSAGE, "")); //$NON-NLS-1$
			info.setName(json.optString(KEY_NAME, "")); //$NON-NLS-1$
			if(json.has(KEY_TIMESTAMP_MODIFIED))
				info.modified = new Date(json.getLong(KEY_TIMESTAMP_MODIFIED));
			else
				info.modified = new Date(0);
			
			if(json.has(KEY_TIMESTAMP_CREATED))
				info.created = new Date(json.getLong(KEY_TIMESTAMP_CREATED));
			else
				info.created = new Date(0);
			
			info.running = json.optBoolean(KEY_RUNNING, true);
			info.setPercentComplete(json.optInt(KEY_PERCENT_COMPLETE, 0));
			String location = json.optString(KEY_LOCATION, null);
			if (location != null)
				info.resultLocation = URI.create(location);
			String resultString = json.optString(KEY_RESULT, null);
			if (resultString != null)
				info.result = ServerStatus.fromJSON(resultString);
			return info;
		} catch (JSONException e) {
			LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Invalid task: " + taskString, e)); //$NON-NLS-1$
			return null;
		}
	}

	public TaskInfo(String userId, String id, boolean idempotent) {
		this.idempotent = idempotent;
		this.userId = userId;
		this.id = id;
		this.modified = new Date();
		this.created = new Date();
	}

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Returns information if task canceling is supported.
	 * @return <code>true</code> if task can be canceled
	 */
	public boolean canBeCanceled() {
		return canBeCanceled;
	}
	
	public void setCanBeCanceled(boolean canBeCanceled){
		this.canBeCanceled = canBeCanceled;
	}
	
	
	public boolean isIdempotent() {
		return idempotent;
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

	/**
	 * Returns the location of the resource representing the result of the computation. 
	 * Returns <code>null</code> if the task has not completed, or if it did not complete successfully
	 * @return The task result location, or <code>null</code>
	 */
	public URI getResultLocation() {
		return resultLocation;
	}

	/**
	 * Returns the status describing the result of the operation, or <code>null</code>
	 * if the operation has not yet completed.
	 * @return The result status
	 */
	public IStatus getResult() {
		return result;
	}

	public String getTaskId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	/**
	 * Returns last modification date.
	 * @return last modification date.
	 */
	public Date getModified() {
		return modified;
	}
	
	public void setModified(Date modified){
		this.modified = modified;
	}
	
	public Date getCreated(){
		return created;
	}

	/**
	 * Returns whether the task is currently running.
	 * @return <code>true</code> if the task is currently running, and
	 * <code>false</code> otherwise.
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Sets a message describing the current task state. 
	 * @param message the message to set
	 * @return Returns this task
	 */
	public TaskInfo setMessage(String message) {
		this.message = message == null ? "" : message; //$NON-NLS-1$
		return this;
	}

	/**
	 * Sets the percent complete of this task. Values below 0 will be rounded up to zero,
	 * and values above 100 will be rounded down to 100;
	 * @param percentComplete an integer between 0 and 100 representing the
	 * current progress state
	 * @return Returns this task
	 */
	public TaskInfo setPercentComplete(int percentComplete) {
		if (percentComplete < 0)
			percentComplete = 0;
		if (percentComplete > 100)
			percentComplete = 100;
		this.percentComplete = percentComplete;
		return this;
	}

	/**
	 * Indicates that this task is completed.
	 * @param status The result status
	 * @return Returns this task
	 */
	public TaskInfo done(IStatus status) {
		this.running = false;
		this.percentComplete = 100;
		this.result = status;
		this.message = status.getMessage();
		this.modified = new Date();
		return this;
	}

	/**
	 * Sets the location of the task result object.
	 * @param location The location of the result resource for the task, or
	 * <code>null</code> if not applicable.
	 * @return Returns this task
	 */
	public TaskInfo setResultLocation(String location) {
		this.resultLocation = URI.create(location);
		return this;
	}

	/**
	 * Sets the location of the task result object.
	 * @param location The location of the result resource for the task, or
	 * <code>null</code> if not applicable.
	 * @return Returns this task
	 */
	public TaskInfo setResultLocation(URI location) {
		this.resultLocation = location;
		return this;
	}

	/**
	 * Returns a JSON representation of this task state.
	 */
	public JSONObject toLightJSON() {
		JSONObject resultObject = new JSONObject();
		try {
			resultObject.put(KEY_RUNNING, isRunning());
			resultObject.put(KEY_MESSAGE, getMessage());
			resultObject.put(KEY_ID, getTaskId());
			resultObject.put(KEY_USER, getUserId());
			resultObject.put(KEY_PERCENT_COMPLETE, getPercentComplete());
			resultObject.put(KEY_TIMESTAMP_MODIFIED, modified.getTime());
			resultObject.put(KEY_CAN_BE_CANCELED, canBeCanceled);
			resultObject.put(KEY_TIMESTAMP_CREATED, created.getTime());
			resultObject.put(KEY_IDEMPOTENT, idempotent);
			resultObject.put(KEY_NAME, name==null ? "" : name);
			if (resultLocation != null)
				resultObject.put(KEY_LOCATION, resultLocation);
			if(result!=null){
				if(!result.isOK()){
					resultObject.put(KEY_FAILED, true);
					resultObject.put(KEY_RESULT, ServerStatus.convert(result).toJSON());
				}
				if(result.getSeverity()==IStatus.CANCEL)
					resultObject.put(KEY_CANCELED, true);
			}
		} catch (JSONException e) {
			//can only happen if key is null
		}
		return resultObject;
	}

	/**
	 * Returns a JSON representation of this task state.
	 */
	public JSONObject toJSON() {
		JSONObject resultObject = new JSONObject();
		try {
			resultObject.put(KEY_RUNNING, isRunning());
			resultObject.put(KEY_MESSAGE, getMessage());
			resultObject.put(KEY_ID, getTaskId());
			resultObject.put(KEY_USER, getUserId());
			resultObject.put(KEY_PERCENT_COMPLETE, getPercentComplete());
			resultObject.put(KEY_TIMESTAMP_MODIFIED, modified.getTime());
			resultObject.put(KEY_TIMESTAMP_CREATED, created.getTime());
			resultObject.put(KEY_CAN_BE_CANCELED, canBeCanceled);
			resultObject.put(KEY_IDEMPOTENT, idempotent);
			resultObject.put(KEY_NAME, name==null ? "" : name);
			if (resultLocation != null)
				resultObject.put(KEY_LOCATION, resultLocation);
			if (result != null) {
				resultObject.put(KEY_RESULT, ServerStatus.convert(result).toJSON());
				if(!result.isOK()){
					resultObject.put(KEY_FAILED, true);
				}
				if(result.getSeverity()==IStatus.CANCEL){
					resultObject.put(KEY_CANCELED, true);
				}
			}
		} catch (JSONException e) {
			//can only happen if key is null
		}
		return resultObject;
	}

	@Override
	public String toString() {
		return "TaskInfo" + toJSON(); //$NON-NLS-1$
	}
}
