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
package org.eclipse.orion.server.docker.servlets;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebSocket
public class DockerSocket {

	private CountDownLatch messageLatch;

	private String outMessage;

	private Session session;

	public String getOutMessage() {
		return this.outMessage;
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		this.session = session;
	}

	@OnWebSocketMessage
	public void onMessage(String msg) {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.servlets.OrionServlet"); //$NON-NLS-1$
		logger.debug("Docker Socket received message " + msg);
		this.outMessage += msg;
		this.messageLatch.countDown();
	}

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.servlets.OrionServlet"); //$NON-NLS-1$
		logger.debug("Docker Socket received close " + reason);
		session.close();
		session = null;
    }

	public void sendCmd(String cmd) {
		try {
			this.messageLatch = new CountDownLatch(1);
			this.outMessage = "";
			this.session.getRemote().sendString(cmd);
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.servlets.OrionServlet"); //$NON-NLS-1$
			logger.debug("Docker Socket sent command " + cmd);
		} catch (IOException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.servlets.OrionServlet"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e);
			session.close();
			session = null;
		}
	}

	public boolean waitResponse(int amount, TimeUnit unit) throws InterruptedException {
		return this.messageLatch.await(amount, unit);
	}

}
