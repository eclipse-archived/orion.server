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

}
