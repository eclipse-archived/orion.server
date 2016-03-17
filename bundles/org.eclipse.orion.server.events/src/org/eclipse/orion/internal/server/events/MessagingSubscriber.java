/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.events;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.orion.server.core.events.IMessageListener;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

public class MessagingSubscriber {
	private Connection connection;
	private Channel channel;
	private volatile Map<String, Set<IMessageListener>> messageListeners = new HashMap<String, Set<IMessageListener>>();
	private Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$

	private static final int MS_BETWEEN_RECONNECTION_ATTEMPTS = 30 * 1000;
	private static final String MESSAGING_TOPIC_EXCHANGE_NAME = "amq.topic"; //$NON-NLS-1$
	private static final String SUBSCRIBE_TOPIC = "devops-services.v1.projects.#"; //$NON-NLS-1$
	private static final String QUEUE_NAME = "orion-notifications-projects"; //$NON-NLS-1$
	private static final String UTF8 = "UTF-8"; //$NON-NLS-1$

	public MessagingSubscriber(final ConnectionFactory factory) {
		super();
		new Thread(new Runnable() {
			@Override
			public void run() {
				boolean connected = false;
				int currentTry = 0;
				while (!connected) {
					try {
						currentTry++;
						connection = factory.newConnection();
						channel = connection.createChannel();
						logger.info("Messaging subscriber successfully connected."); //$NON-NLS-1$
						connected = true;
					} catch (IOException e) {
						try {
							/*
							 * Ensures we don't spam the logs, but only throw an exception stack trace
							 * every 50 failed attempts (50*30s = 1500s = 25mins).
							 */
							if (currentTry % 50 == 1) {
								logger.error("Unable to connect messaging subscriber", e); //$NON-NLS-1$
							}
							Thread.sleep(MS_BETWEEN_RECONNECTION_ATTEMPTS);
						} catch (InterruptedException e1) {
							/* see http://www.ibm.com/developerworks/java/library/j-jtp05236/index.html?ca=drs- */
							Thread.currentThread().interrupt();
						}
					}
				}
				connection.addShutdownListener(new ShutdownListener() {
					/* see "Shutdown Protocol" @ https://www.rabbitmq.com/api-guide.html */
					@Override
					public void shutdownCompleted(ShutdownSignalException cause) {
						if (connection != null) {
							/* indicates that we did not initiate this shutdown in destroy() */
							logger.warn("Detected shutdown signal from messaging broker for subscriber.  Will attempt to reconnect."); //$NON-NLS-1$
							channel = null;
							connection = null;
							run();
						}
					}
				});
				try {
					listenToMessages();
				} catch (IOException e) {
					logger.error("Failed to subscribe for incoming messages", e); //$NON-NLS-1$
				}
			}
		}).start();
	}

	public void destroy() {
		if (channel != null) {
			try {
				channel.close();
			} catch (Exception e) {
				logger.warn("Unable to close messaging subscriber channel gracefully", e); //$NON-NLS-1$
			}
			channel = null;
		}
		if (connection != null) {
			try {
				connection.close();
			} catch (IOException e) {
				logger.warn("Unable to close messaging subscriber connection gracefully", e); //$NON-NLS-1$
			}
			connection = null;
		}
	}

	private void listenToMessages() throws IOException {
		QueueingConsumer consumer = new QueueingConsumer(channel);
		channel.queueDeclare(QUEUE_NAME, true, false, false, null);
		channel.queueBind(QUEUE_NAME, MESSAGING_TOPIC_EXCHANGE_NAME, SUBSCRIBE_TOPIC);
	    channel.basicConsume(QUEUE_NAME, true, consumer);

		while (true) {
	    	QueueingConsumer.Delivery delivery;

	    	String message = null;
	    	try {
	    		delivery = consumer.nextDelivery();
	    		message = new String(delivery.getBody(), UTF8);
	    		logger.debug("Received message: " + message); //$NON-NLS-1$
	    		String topic = delivery.getEnvelope().getRoutingKey();
logger.warn("ASDF raw topic: " + topic);
				topic = topic.replaceAll("\\.", "/"); //$NON-NLS-1$ //$NON-NLS-2$
logger.warn("ASDF fixed topic: " + topic);
				handleMessage(topic, message);
	    	} catch (ShutdownSignalException e) {
	    		logger.error("Messaging subscriber connection was shutdown", e); //$NON-NLS-1$
	    		/* this is handled by the shutdown listener in connectClient() */
	    		return;
	    	} catch (ConsumerCancelledException e) {
	    		logger.error("Messaging consumer was cancelled while waiting", e); //$NON-NLS-1$
	    	} catch (InterruptedException e) {
	    		/* see http://www.ibm.com/developerworks/java/library/j-jtp05236/index.html?ca=drs- */
	    		Thread.currentThread().interrupt();
	    	} catch (IOException e) {
	    		logger.error("IOException with received message", e); //$NON-NLS-1$
	    	}
	    }
	}

	private void handleMessage(String topic, String payload) {
logger.warn("ASDF received: " + topic + "..." + payload);
		JSONObject message = new JSONObject();
		try {
			message.put("Topic", topic); //$NON-NLS-1$
			message.put("QoS", 1); //$NON-NLS-1$
			message.put("Message", new JSONObject(payload)); //$NON-NLS-1$
		} catch (JSONException e) {
			try {
				message.put("Message", payload); //$NON-NLS-1$
			} catch (JSONException ex) {
				logger.warn("Unable to process received message: " + payload, ex); //$NON-NLS-1$
				return;
			}
		}

		Set<String> topics = messageListeners.keySet();
logger.warn("ASDF topics set size: " + topics.size());
		for (String registeredTopic : topics) {
logger.warn("ASDF registeredTopic: " + registeredTopic);
			String scrubbedTopic = registeredTopic.replaceAll("/#", ".*").replaceAll("#", ".*").replaceAll("\\\\+", ".+"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
logger.warn("ASDF scrubbedTopic: " + scrubbedTopic);
			boolean matches = false;
			try {
				matches = Pattern.matches(scrubbedTopic, topic);
			} catch (PatternSyntaxException e) {
				logger.warn("Ignoring malformed topic on received message:" + registeredTopic);
			}
logger.warn("ASDF match? " + matches);
			if (matches) {
				if (logger.isDebugEnabled()) {
					logger.debug("Pattern matched. Topic: " + topic + " . Registered topic: " + registeredTopic); //$NON-NLS-1$ //$NON-NLS-2$
				}
				Set<IMessageListener> topicListeners = messageListeners.get(registeredTopic);
				if (topicListeners != null && !topicListeners.isEmpty()) {
logger.warn("ASDF topicListeners set size: " + topicListeners.size());
					for (IMessageListener listener : topicListeners) {
logger.warn("ASDF listener notified");
						listener.receiveMessage(message);
					}
				} else {
logger.warn("ASDF topicListeners set size is 0 or null, topicListeners=" + topicListeners);
					logger.info("Topic listener could not be found to topic we were subscribed to. Topic: " + registeredTopic); //$NON-NLS-1$
				}
			}
		}
	}

	public synchronized void receive(String topic, IMessageListener messageListener) {
//		if (logger.isDebugEnabled()) {
//			logger.debug("Added listener for messaging topic " + topic + " " + messageListener); //$NON-NLS-1$ //$NON-NLS-2$
logger.warn("Added listener for messaging topic " + topic + " " + messageListener); //$NON-NLS-1$ //$NON-NLS-2$
//		}
		Set<IMessageListener> topicListeners = messageListeners.get(topic);
		if (topicListeners == null) {
			topicListeners = new HashSet<IMessageListener>();
			messageListeners.put(topic, topicListeners);
		}
		topicListeners.add(messageListener);
	}

	public void stopReceiving(String topic, IMessageListener messageListener) {
//		if (logger.isDebugEnabled()) {
//			logger.debug("Removed listener for messaging topic " + topic + " " + messageListener); //$NON-NLS-1$ //$NON-NLS-2$
logger.warn("Removed listener for messaging topic " + topic + " " + messageListener); //$NON-NLS-1$ //$NON-NLS-2$
//		}
		Set<IMessageListener> topicListeners = messageListeners.get(topic);
		if (topicListeners != null) {
			topicListeners.remove(messageListener);
		}
	}
}
