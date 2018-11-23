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

public class CorruptedTaskException extends TaskOperationException {
	private static final long serialVersionUID = 911965875723016263L;
	private String taskInfo;

	public CorruptedTaskException(String taskInfo) {
		super("Corrupted Task: " + taskInfo);
		this.taskInfo = taskInfo;
	}

	public CorruptedTaskException(String taskInfo, Throwable cause) {
		super("Corrupted Task: " + taskInfo, cause);
		this.taskInfo = taskInfo;
	}

	public String getTaskInfo() {
		return taskInfo;
	}
}
