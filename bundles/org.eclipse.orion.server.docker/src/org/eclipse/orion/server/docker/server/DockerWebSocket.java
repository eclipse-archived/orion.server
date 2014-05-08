/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.docker.server;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles a web socket connection to a docker container.
 *  
 * @author Anthony Hunter
 * @author Bogdan Gheorghe
 */
@WebSocket
public class DockerWebSocket {

	private CountDownLatch messageLatch;

	private String response;

	private Session session;
	
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.docker"); //$NON-NLS-1$

	/**
	 * Get the current response received from the web socket.
	 * 
	 * @return the current response.
	 */
	public String getResponse() {
		return this.response;
	}

	public Session getSession() {
		return this.session;
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		this.session = session;
	}

	@OnWebSocketMessage
	public void onMessage(String msg) {
		if (logger.isDebugEnabled()) {
			// Create a JSONObject as a good way to print out the control characters as \r, \n, etc.
			JSONObject jsonObject = new JSONObject();
			String received = msg;
			try {
				jsonObject.put("message", msg);
				received = jsonObject.toString();
			} catch (JSONException e) {
				// do not do anything here, just use the message
			}
			logger.debug("Docker Socket received: " + received);
		}
		this.response += msg;
		if (messageLatch != null) {
			this.messageLatch.countDown();
		}
	}

	@OnWebSocketClose
	public void onClose(int statusCode, String reason) {
		if (logger.isDebugEnabled()) {
			logger.debug("Docker Socket closed" + (statusCode != StatusCode.NORMAL ? ": " + reason : ""));
		}
		session.close();
	}

	/**
	 * Send the message through the web socket.
	 * 
	 * @param msg the message.
	 */
	public void send(String msg) {
		try {
			if (!session.isOpen()) {
				// session has been closed, just return
				return;
			}
			this.messageLatch = new CountDownLatch(1);
			this.response = "";
			this.session.getRemote().sendString(msg);
			if (logger.isDebugEnabled()) {
				// Create a JSONObject as a good way to print out the control characters as \r, \n, etc.
				JSONObject jsonObject = new JSONObject();
				String sent = msg;
				try {
					jsonObject.put("message", msg);
					sent = jsonObject.toString();
				} catch (JSONException e) {
					// do not do anything here, just use the cmd
				}
				logger.debug("Docker Socket sent: " + sent);
			}
		} catch (IOException e) {
			logger.error(e.getLocalizedMessage(), e);
			session.close();
		}
	}

	/**
	 * Wait for a message on the web socket.
	 * 
	 * @return true if a message has been received.
	 * @throws InterruptedException
	 */
	public boolean waitResponse() throws InterruptedException {
		return this.messageLatch.await(5, TimeUnit.SECONDS);
	}
}
