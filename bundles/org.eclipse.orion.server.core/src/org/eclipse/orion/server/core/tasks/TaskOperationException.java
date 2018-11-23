/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.tasks;

/**
 * Thrown when attempted to perform task operation that is not supported by this task. 
 *
 */
public class TaskOperationException extends Exception {

	private static final long serialVersionUID = 2207949082734854837L;
	
	public TaskOperationException(){
		super();
	}
	
	public TaskOperationException(String message){
		super(message);
	}
	
	public TaskOperationException(String message, Throwable cause){
		super(message, cause);
	}

}
