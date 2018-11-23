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
package org.eclipse.orion.server.docker.server;

import org.eclipse.jetty.websocket.client.WebSocketClient;

/**
 * A connection to a docker container using a web socket. 
 *  
 * @author Anthony Hunter
 * @author Bogdan Gheorghe
 */
public class DockerContainerConnection {
	
	private DockerWebSocket webSocket;
	
	private WebSocketClient webSocketClient;

	public DockerContainerConnection(DockerWebSocket socket, WebSocketClient client) {
		this.webSocket = socket;
		this.webSocketClient = client;
	}

	public DockerWebSocket getWebSocket() {
		return this.webSocket;
	}

	public WebSocketClient getWebSocketClient() {
		return this.webSocketClient;
	}

}