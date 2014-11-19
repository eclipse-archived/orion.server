/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.loggregator;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggregatorClient {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	public void start(Target target, String loggregatorLocation, LoggregatorListener listener) throws Exception {
		logger.debug(NLS.bind("About to connect: {0}", loggregatorLocation));

		SslContextFactory sslContextFactory = new SslContextFactory(true);
		WebSocketClient client = new WebSocketClient(sslContextFactory);
		LoggregatorSocket socket = new LoggregatorSocket(listener);
		try {
			client.start();
			URI loggregatorUri = new URI(loggregatorLocation);
			ClientUpgradeRequest request = new ClientUpgradeRequest();
			request.setHeader("Authorization", "bearer " + target.getCloud().getAccessToken().getString("access_token"));

			client.connect(socket, loggregatorUri, request);
			logger.debug(NLS.bind("Connecting to: {0}", loggregatorUri));
			socket.awaitClose(25, TimeUnit.SECONDS);
		} finally {
			client.stop();
		}
	}
}