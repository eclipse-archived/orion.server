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
package org.eclipse.orion.internal.server.events;

import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.events.IEventService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The events service.
 * 
 * @author Anthony Hunter
 */
public class EventService implements IEventService {

	private MqttClient mqttClient;
	private MqttConnectOptions mqttConnectOptions;
	private Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$

	public EventService() {
		this.mqttClient = initClient();
	}

	private MqttClient initClient() {
		mqttConnectOptions = new MqttConnectOptions();
		String serverURI = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_HOST, null);
		if (serverURI == null) {
			//no MQTT message broker host defined
			if (logger.isDebugEnabled()) {
				logger.warn("No MQTT message broker specified in the orion.conf with " + ServerConstants.CONFIG_EVENT_HOST); //$NON-NLS-1$
			}
			return null;
		}
		if (logger.isInfoEnabled()) {
			logger.info("Using MQTT message broker at " + serverURI);
		}
		String username = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_USERNAME, null);
		if (username != null) {
			mqttConnectOptions.setUserName(username);
		}
		String password = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_PASSWORD, null);
		if (password != null) {
			mqttConnectOptions.setPassword(password.toCharArray());
		}

		try {
			return new MqttClient(serverURI, MqttClient.generateClientId(), new MemoryPersistence());
		} catch (MqttException e) {
			logger.warn("Failed to initialize MQTT event client", e); //$NON-NLS-1$
		}

		return null;
	}

	public void publish(String topic, JSONObject message) {
		//nothing to do if we have no MQTT event client configured
		if (mqttClient == null) {
			return;
		}
		MqttMessage mqttMessage = new MqttMessage(message.toString().getBytes());
		try {
			mqttClient.connect(mqttConnectOptions);
			mqttClient.publish(topic, mqttMessage);
			mqttClient.disconnect();
		} catch (MqttException e) {
			logger.warn("Failure publishing event on topic: " + topic, ", message: " + message.toString(), e); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (logger.isDebugEnabled()) {
			try {
				logger.debug("Published event on topic: " + topic + " message:\n" + message.toString(4));
			} catch (JSONException e) {
				logger.error(e.getLocalizedMessage(), e);
			}
		}
	}

}
