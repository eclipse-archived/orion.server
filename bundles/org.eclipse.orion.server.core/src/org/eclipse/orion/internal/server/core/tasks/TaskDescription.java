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
package org.eclipse.orion.internal.server.core.tasks;

public class TaskDescription {

	private String taskId;
	private String userId;
	private boolean keep;

	public TaskDescription(String userId, String taskId, boolean keep) {
		super();
		this.taskId = taskId;
		this.userId = userId;
		this.keep = keep;
	}

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public boolean isKeep() {
		return keep;
	}

	public void setKeep(boolean keep) {
		this.keep = keep;
	}

	@Override
	public boolean equals(Object o) {
		if(!(o instanceof TaskDescription)){
			return false;
		}
		TaskDescription td = (TaskDescription) o;
		return (this.getUserId().equals(td.getUserId()) && (this.getTaskId().equals(td.getTaskId()) && this.isKeep()==td.isKeep()));
	}

	@Override
	public int hashCode() {
		return (this.getUserId().hashCode() + this.getTaskId().hashCode() + (this.isKeep() ? 1: 0))/2;
	}
	
	
}
