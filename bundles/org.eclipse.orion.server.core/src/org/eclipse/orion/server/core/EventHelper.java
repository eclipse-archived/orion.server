/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

import java.net.URI;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.logging.LoggerFactory;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

/**
 * Helper method for publishing MQTT events from an Orion server.
 */
public class EventHelper {
	private URI brokerLocation;
	private MqttClient broker;
	private Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.core.events"); //$NON-NLS-1$

	public EventHelper(URI location) {
		this.brokerLocation = location;
		this.broker = initClient();
	}

	private MqttClient initClient() {
		if (brokerLocation == null) {
			logger.info("No event broker configured"); //$NON-NLS-1$
			return null;
		}
		try {
			return new MqttClient(brokerLocation.toString(), MqttClient.generateClientId(), new MemoryPersistence());
		} catch (MqttException e) {
			logger.warn("Failed to initialize MQTT event client", e); //$NON-NLS-1$
		}

		return null;
	}

	public void publish(String topic, JSONObject payload) {
		//nothing to do if we have no broker configured
		if (broker == null)
			return;
		MqttMessage message = new MqttMessage(payload.toString().getBytes());
		try {
			MqttConnectOptions options = getOptions();
			options.setUserName(PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_USER));
			final String pass = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_PASSWORD);
			if (pass != null)
				options.setPassword(pass.toCharArray());
			broker.connect(options);
			broker.publish(topic, message);
			broker.disconnect();
		} catch (MqttException e) {
			logger.warn("Failure publishing event on topic: " + topic, ", payload: " + payload.toString(), e); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private MqttConnectOptions getOptions() {
		return null;
	}

}
