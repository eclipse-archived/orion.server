/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.docker;

/**
 * The response received from the Docker Remote API call.
 *  
 * @author Anthony Hunter
 */
public class DockerResponse {

	/**
	 * Http status code returned from Docker
	 * 200 - no error, OK
	 * 201 - no error, created
	 * 204 - no error, started or stopped
	 * 400 - bad parameter
	 * 404 - no such image or container
	 * 500 - server error
	 */
	public enum StatusCode {
		BAD_PARAMETER, CREATED, DELETED, NO_SUCH_CONTAINER, NO_SUCH_IMAGE, OK, SERVER_ERROR, STARTED, STOPPED
	};

	private StatusCode statusCode;

	/**
	 * The status message in the case where the status code is a server error.
	 */
	private String statusMessage;

	public DockerResponse() {
		super();
		// set the status code to server error on creation, we have not connected to the docker server yet.
		statusCode = StatusCode.SERVER_ERROR;
	}

	public StatusCode getStatusCode() {
		return statusCode;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public void setStatusCode(StatusCode statusCode) {
		this.statusCode = statusCode;
	}

	public void setStatusMessage(String statusMessage) {
		this.statusMessage = statusMessage;
	}
}
