package org.eclipse.orion.server.docker.server;

import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.orion.server.docker.servlets.DockerSocket;

public class ClientSocket {
	DockerSocket socket;
	WebSocketClient client;
	public ClientSocket(DockerSocket socket, WebSocketClient client) {
		this.socket = socket;
		this.client = client;
	}
	
	public DockerSocket getSocket() {
		return this.socket;
	}
	
	public WebSocketClient getSocketClient() {
		return this.client;
	}

}