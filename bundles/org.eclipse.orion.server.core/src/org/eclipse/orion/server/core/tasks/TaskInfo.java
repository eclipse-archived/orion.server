/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.tasks;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.core.tasks.TaskDescription;
import org.eclipse.orion.server.core.ServerStatus;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a snapshot of the state of a long running task.
 */
public class TaskInfo {
	public static final String KEY_TYPE = "type";
	public static final String KEY_TIMESTAMP = "timestamp";
	public static final String KEY_LENGTH_COMPUTABLE = "lengthComputable";
	public static final String KEY_LOADED = "loaded";
	public static final String KEY_TOTAL = "total";
	public static final String KEY_MESSAGE = "message";
	public static final String KEY_EXPIRES = "expires";
	public static final String KEY_RESULT = "Result";
	public static final String KEY_CANCELABLE = "cancelable";
	public static final String KEY_URI_UNQUALIFICATION = "uriUnqualStrategy";

	private static final String STATUS_LOADSTART = "loadstart";
	private static final String STATUS_PROGRESS = "progress";
	private static final String STATUS_ERROR = "error";
	private static final String STATUS_ABORT = "abort";
	private static final String STATUS_LOAD = "load";
	private static final String STATUS_LOADEND = "loadend";
	
	public enum TaskStatus {

		LOADSTART(STATUS_LOADSTART), PROGRESS(STATUS_PROGRESS), ERROR(STATUS_ERROR), ABORT(STATUS_ABORT), LOAD(STATUS_LOAD), LOADEND(STATUS_LOADEND);

		private final String statusString;

		TaskStatus(String statusString) {
			this.statusString = statusString;
		}

		public String toString() {
			return this.statusString;
		}

		public static TaskStatus fromString(String taskString) {
			if (STATUS_LOADSTART.equals(taskString)) {
				return LOADSTART;
			}
			if (STATUS_PROGRESS.equals(taskString)) {
				return PROGRESS;
			}
			if (STATUS_ERROR.equals(taskString)) {
				return ERROR;
			}
			if (STATUS_LOAD.equals(taskString)) {
				return LOAD;
			}
			if (STATUS_LOADEND.equals(taskString)) {
				return LOADEND;
			}
			return ABORT;
		}
	};

	private final String id;
	private final String userId;
	private boolean keep = true;
	private boolean lengthComputable = false;
	private Date timestamp;
	private Date expires;
	private TaskStatus status = TaskStatus.LOADSTART;
	private int loaded = 0;
	private int total = 0;
	private String msg;
	private IStatus result;
	private boolean cancelable = false;
	private IURIUnqualificationStrategy strategy;
	
	static HashMap<String, IURIUnqualificationStrategy> registry = new HashMap<String, IURIUnqualificationStrategy>();
	static void addStrategy(IURIUnqualificationStrategy strategy) {
		String name = strategy.getName();
		if (!registry.containsKey(name)) {
			registry.put(name, strategy);
		}
	}
	static IURIUnqualificationStrategy getStrategy(String name) {
		return registry.get(name);
	}
	
	/**
	 * Returns a task object based on its JSON representation. Returns
	 * null if the given string is not a valid JSON task representation.
	 * This function does not set <code>canBeCanceled</code>. The caller
	 * must find out himself if the task can be canceled and set this flag.
	 * @throws CorruptedTaskException 
	 */
	public static TaskInfo fromJSON(TaskDescription description, String taskString) throws CorruptedTaskException {
		TaskInfo info;
		try {
			JSONObject json = new JSONObject(taskString);
			info = new TaskInfo(description.getUserId(), description.getTaskId(), description.isKeep());
			if (json.has(KEY_EXPIRES))
				info.expires = new Date(json.getLong(KEY_EXPIRES));

			if (json.has(KEY_TIMESTAMP))
				info.timestamp = new Date(json.getLong(KEY_TIMESTAMP));

			info.lengthComputable = json.optBoolean(KEY_LENGTH_COMPUTABLE);
			if (json.has(KEY_LOADED))
				info.loaded = json.optInt(KEY_LOADED);
			if (json.has(KEY_TOTAL))
				info.total = json.getInt(KEY_TOTAL);
			if (json.has(KEY_MESSAGE))
				info.msg = json.getString(KEY_MESSAGE);

			if (json.has(KEY_TYPE))
				info.status = TaskStatus.fromString(json.optString(KEY_TYPE));

			if (json.has(KEY_RESULT))
				info.result = ServerStatus.fromJSON(json.optString(KEY_RESULT));
			
			if (json.has(KEY_URI_UNQUALIFICATION)) 
				info.strategy = getStrategy(json.optString(KEY_URI_UNQUALIFICATION));

			return info;
		} catch (JSONException e) {
			throw new CorruptedTaskException(taskString, e);
		}
	}

	public TaskInfo(String userId, String id, boolean keep) {
		this.keep = keep;
		this.userId = userId;
		this.id = id;
		this.timestamp = new Date();
	}

	public boolean isRunning() {
		return !(status == TaskStatus.LOAD || status == TaskStatus.ERROR || status == TaskStatus.ABORT || status == TaskStatus.LOADEND);
	}

	public boolean isKeep() {
		return keep;
	}

	public void setKeep(boolean keep) {
		this.keep = keep;
	}

	public boolean isLengthComputable() {
		return lengthComputable;
	}

	public void setLengthComputable(boolean lengthComputable) {
		this.lengthComputable = lengthComputable;
	}

	public Long getTimestamp() {
		return timestamp==null ? null : timestamp.getTime();
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = new Date(timestamp);
	}

	public Long getExpires() {
		return expires==null ? null : expires.getTime();
	}

	public void setExpires(long expires) {
		this.expires = new Date(expires);
	}

	public TaskStatus getStatus() {
		return status;
	}

	public void setStatus(TaskStatus status) {
		this.status = status;
	}
	
	public IURIUnqualificationStrategy getUnqualificationStrategy() {
		return this.strategy;
	}
	
	public void setUnqualificationStrategy (IURIUnqualificationStrategy strategy) {
		addStrategy(strategy);
		this.strategy = strategy;
	}

	public int getLoaded() {
		return loaded;
	}

	public void setLoaded(int loaded) {
		this.loaded = loaded;
	}

	public String getMessage() {
		return msg;
	}

	public void setMessage(String msg) {
		this.msg = msg;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public String getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public IStatus getResult() {
		return result;
	}
	
	public boolean isCancelable() {
		return cancelable;
	}

	public void setCancelable(boolean cancelable) {
		this.cancelable = cancelable;
	}

	public void done(IStatus result) {
		this.result = result;
		switch(result.getSeverity()){
			case IStatus.OK:
			case IStatus.INFO:
				this.status = TaskStatus.LOADEND;
				break;
			case IStatus.ERROR:
			case IStatus.WARNING:
				this.status = TaskStatus.ERROR;
				break;
			default:
				this.status = TaskStatus.ABORT;
		}
		if(expires!=null){
			return;
		}
		if(!keep){
			//if task info should not be kept it will expire in 15 minutes
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.MINUTE, 15);
			expires = cal.getTime();
		} else {
			//if task info should be kept it will be default expire in 7 days
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DATE, 7);
			expires = cal.getTime();
		}
	}

	/**
	 * Returns a JSON representation of this task state.
	 */
	public JSONObject toLightJSON() {
		JSONObject resultObject = new JSONObject();
		try {
			resultObject.put(KEY_LENGTH_COMPUTABLE, isLengthComputable());
			if (isLengthComputable()) {
				resultObject.put(KEY_LOADED, getLoaded());
				resultObject.put(KEY_TOTAL, getTotal());
				resultObject.put(KEY_MESSAGE, getMessage());
			}
			if(getTimestamp()!=null)
				resultObject.put(KEY_TIMESTAMP, getTimestamp());
			if(getExpires()!=null)
				resultObject.put(KEY_EXPIRES, getExpires());
			IURIUnqualificationStrategy strategy = getUnqualificationStrategy();
			if(strategy != null) {
				resultObject.put(KEY_URI_UNQUALIFICATION, strategy.getName());
			}
			resultObject.put(KEY_TYPE, getStatus().toString());
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
			resultObject.put(KEY_LENGTH_COMPUTABLE, isLengthComputable());
			if (isLengthComputable()) {
				resultObject.put(KEY_LOADED, getLoaded());
				resultObject.put(KEY_TOTAL, getTotal());
				resultObject.put(KEY_MESSAGE, getMessage());
			}
			if (result != null)
				resultObject.put(KEY_RESULT, ServerStatus.convert(result).toJSON());
			if(getTimestamp()!=null)
				resultObject.put(KEY_TIMESTAMP, getTimestamp());
			if(getExpires()!=null)
				resultObject.put(KEY_EXPIRES, getExpires());
			if(isCancelable())
				resultObject.put(KEY_CANCELABLE, isCancelable());
			IURIUnqualificationStrategy strategy = getUnqualificationStrategy();
			if(strategy != null) {
				resultObject.put(KEY_URI_UNQUALIFICATION, strategy.getName());
			}
			resultObject.put(KEY_TYPE, getStatus().toString());
		} catch (JSONException e) {
			//can only happen if key is null
		}
		return resultObject;
	}
	
	@Override
	public String toString() {
		return "TaskInfo: " + toJSON(); //$NON-NLS-1$
	}
}
