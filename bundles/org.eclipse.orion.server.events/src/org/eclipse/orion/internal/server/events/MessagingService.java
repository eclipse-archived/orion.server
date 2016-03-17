/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.events;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.events.IMessageListener;
import org.eclipse.orion.server.core.events.IMessagingService;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * RabbitMQ-backed messaging service.
 */
public class MessagingService implements IMessagingService {
	private ConnectionFactory factory = new ConnectionFactory();
	private Connection connection;
	private Channel channel;
	private MessagingSubscriber subscriber;
	Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
	private int retryCount = 0;

	private static AtomicBoolean reconnectionLock = new AtomicBoolean(false);

	private static final int DEFAULT_PORT = 5672;
	private static final String MESSAGING_TOPIC_EXCHANGE_NAME = "amq.topic"; //$NON-NLS-1$
	private static final String UTF8 = "UTF-8"; //$NON-NLS-1$
	private static final String KEY_MESSAGING_URI = "orion.messaging.uri"; //$NON-NLS-1$
	private static final String KEY_MESSAGING_USERNAME = "orion.messaging.user"; //$NON-NLS-1$
	private static final String KEY_MESSAGING_PASSWORD = "orion.messaging.password"; //$NON-NLS-1$
	private static final String KEY_MESSAGING_KEYSTORE = "orion.messaging.keyStore"; //$NON-NLS-1$
	private static final String KEY_MESSAGING_KEYTYPE = "orion.messaging.keyType"; //$NON-NLS-1$
	private static final String KEY_MESSAGING_KEYPASSWORD = "orion.messaging.keyPassword"; //$NON-NLS-1$
	private static final String KEY_MESSAGING_TRUSTSTORE = "orion.messaging.trustStore"; //$NON-NLS-1$
	private static final String KEY_MESSAGING_VIRTUALHOST = "orion.messaging.virtualHost"; //$NON-NLS-1$

	public MessagingService() {
		super();
		/* Initialize client asynchronously to avoid blocking startup */
		new Job("Initializing Client") { //$NON-NLS-1$
			protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
				initClient();
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	@Override
	public boolean clientConnected() {
		return connection != null && channel != null;
	}

	private void connectMessagingClient() throws IOException {
		if (clientConnected()) {
			return;
		}

		connection = factory.newConnection();
		channel = connection.createChannel();
		if (subscriber == null) {
			subscriber = new MessagingSubscriber(factory);
		}
		/* see "Shutdown Protocol" @ https://www.rabbitmq.com/api-guide.html */
		connection.addShutdownListener(new ShutdownListener() {
			@Override
			public void shutdownCompleted(ShutdownSignalException cause) {
				logger.warn("Detected shutdown signal from messaging broker for client.  Scheduling a job to reconnect."); //$NON-NLS-1$
				channel = null;
				connection = null;
				new ReconnectMessagingClientJob(MessagingService.this).schedule();	
			}
		});
	}

	public void destroy() {
		if (channel != null) {
			try {
				channel.close();
			} catch (Exception e) {
				logger.warn("Unable to close messaging channel gracefully", e); //$NON-NLS-1$
			}
			channel = null;
		}
		if (connection != null) {
			try {
				connection.close();
			} catch (IOException e) {
				logger.warn("Unable to close messaging connection gracefully", e); //$NON-NLS-1$
			}
			connection = null;
		}
		if (subscriber != null) {
			subscriber.destroy();
			subscriber = null;
		}
	}

	private void initClient() {
		String brokerURI = PreferenceHelper.getString(KEY_MESSAGING_URI);
		if (brokerURI == null) {
			logger.warn("Messaging is disabled because key is not configured: " + KEY_MESSAGING_URI); //$NON-NLS-1$
			return;
		}

		String username = PreferenceHelper.getString(KEY_MESSAGING_USERNAME, null);
		String password = PreferenceHelper.getString(KEY_MESSAGING_PASSWORD, null);
		String vHost = PreferenceHelper.getString(KEY_MESSAGING_VIRTUALHOST, null);
		String keyType = PreferenceHelper.getString(KEY_MESSAGING_KEYTYPE, null);
		String keyPassword = PreferenceHelper.getString(KEY_MESSAGING_KEYPASSWORD, null);
		String keyStore = PreferenceHelper.getString(KEY_MESSAGING_KEYSTORE, null);
		String trustStore = PreferenceHelper.getString(KEY_MESSAGING_TRUSTSTORE, null);

		URI uri = null;
		try {
			uri = new URI(brokerURI);
		} catch (URISyntaxException e) {
			this.logger.error("Messaging host resolution failed: " + e.toString()); //$NON-NLS-1$
			return;
		}

		int port = uri.getPort();
		if (port == -1) {
			this.logger.warn("Messaging port was not specified, using default value of: " + DEFAULT_PORT); //$NON-NLS-1$
			port = DEFAULT_PORT;
		}
		logger.info("Using messaging broker at " + uri.getHost() + ":" + port); //$NON-NLS-1$ //$NON-NLS-2$

        factory.setHost(uri.getHost());
        factory.setPort(port);
        if (isDefined(vHost)) {
        	factory.setVirtualHost(vHost);
        }
        if (isDefined(username)) {
        	factory.setUsername(username);
        }
        if (isDefined(password)) {
        	factory.setPassword(password);
        }
        if (isDefined(keyType) && isDefined(keyPassword) && isDefined(keyStore) && isDefined(trustStore)) {
        	char[] keyPassphrase = keyPassword.toCharArray();
        	try {
	            KeyStore ks = KeyStore.getInstance(keyType);
	            ks.load(new FileInputStream(keyStore), keyPassphrase);

	            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
	            kmf.init(ks, keyPassphrase);

	            char[] trustPassphrase = keyPassword.toCharArray();
	            KeyStore tks = KeyStore.getInstance(keyType);
	            tks.load(new FileInputStream(trustStore), trustPassphrase);

	            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	            tmf.init(tks);

	            SSLContext c = SSLContext.getInstance("TLSv1"); //$NON-NLS-1$
	            c.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
	            factory.useSslProtocol(c);
        	} catch (Exception e) {
        		this.logger.error("Unable to set up a secure AMQP connection", e); //$NON-NLS-1$
        	}
        }

        try {
			this.connectMessagingClient();
			logger.info("Messaging client successfully connected."); //$NON-NLS-1$
		} catch (Exception e) {
			logger.warn("Connection to messaging broker could not be established. Scheduling job to reconnect.", e); //$NON-NLS-1$
			new ReconnectMessagingClientJob(this).schedule();
		}
	}

	@Override
	public void publish(String topic, JSONObject message) {
		try {
			logger.info("Publishing message on topic: " + topic + " message:\n" + message.toString(4)); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (JSONException e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		if (!clientConnected()) {
			logger.info("Messaging client is not connected, publish aborted"); //$NON-NLS-1$
			return;
		}

		try {
			channel.basicPublish(MESSAGING_TOPIC_EXCHANGE_NAME, topic, 
					MessageProperties.PERSISTENT_TEXT_PLAIN, message.toString().getBytes(UTF8));
		} catch (IOException e) {
			logger.error("Message publish failed", e); //$NON-NLS-1$
			return;
		}
	}

	@Override
	public synchronized void receive(String topic, IMessageListener messageListener) {
		if (this.subscriber == null) {
			logger.warn("Unable to add messaging receive listener, subscriber is null"); //$NON-NLS-1$
			return;
		}
		this.subscriber.receive(topic, messageListener);
	}

	@Override
	public void reconnectMessagingClient() {
		if (!clientConnected() && reconnectionLock.compareAndSet(false, true)) {
			try {
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							retryCount++;
							connectMessagingClient();
							if (clientConnected()) {
								logger.info("Messaging client connection reestablished!"); //$NON-NLS-1$
								retryCount = 0;
							} else {
								if (retryCount % 50 == 1) {
									logger.warn("Failed to reconnect messaging client, continuing to try"); //$NON-NLS-1$
								}
							}
						} catch (IOException e) {
							/*
							 * Ensures we don't spam the logs, but only throw an exception stack trace
							 * every 50 failed attempts (50*30s = 1500s = 25mins).
							 */
							if (retryCount % 50 == 1) {
								logger.warn("Failed to reconnect messaging client, continuing to try", e); //$NON-NLS-1$
							}
						} finally {
							reconnectionLock.set(false);
						}
					}
				}).start();
			} catch (Exception e) {
				this.logger.error("Unexpected exception occurred", e); //$NON-NLS-1$
				reconnectionLock.set(false);
			}
		}
	}

	@Override
	public void stopReceiving(String topic, IMessageListener messageListener) {
		if (this.subscriber == null) {
			logger.warn("Unable to remove messaging receive listener, subscriber is null"); //$NON-NLS-1$
			return;
		}
		this.subscriber.stopReceiving(topic, messageListener);
	}

	boolean isDefined(String value) {
		return value != null && !value.isEmpty();
	}
}
