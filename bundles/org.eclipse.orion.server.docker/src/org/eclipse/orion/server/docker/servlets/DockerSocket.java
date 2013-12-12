package org.eclipse.orion.server.docker.servlets;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

@WebSocket
public class DockerSocket {
	Session session;
	CountDownLatch messageLatch;
	String outMessage;
	@OnWebSocketConnect
	public void onConnect(Session session) {
		this.session = session;
		//System.out.println("Connected");
	}

	@OnWebSocketMessage
	public void onMessage(String msg) {
		//System.out.println("Got msg: " + msg);
		this.outMessage +=  msg;
		this.messageLatch.countDown();
	}

	public void sendCmd(String cmd) {
		try {
			this.messageLatch = new CountDownLatch(1);
			this.outMessage = "";
			this.session.getRemote().sendString(cmd);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public boolean waitResponse(int amount, TimeUnit unit) throws InterruptedException {
		return this.messageLatch.await(amount, unit);
	}
	
	public String getOutMessage() {
		return this.outMessage;
	}

}
