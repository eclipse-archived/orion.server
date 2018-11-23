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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.events.IEventService;
import org.eclipse.orion.server.core.events.IMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
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
	
	private volatile Map<String, Set<IMessageListener>>  messageListeners = new HashMap<String, Set<IMessageListener>>();
	private Callback callback;
	
	private class Callback implements MqttCallback{
		
		public void connectionLost(Throwable e) {
			//Do nothing
		}

		public void deliveryComplete(IMqttDeliveryToken token) {
			// Do nothing
		}

		public void messageArrived(String topic, MqttMessage msg) throws Exception {
			if (logger.isDebugEnabled()) {
				logger.debug("Message arrived " + (msg == null ? null : msg.toString()) + " topic " + topic);
			}
			JSONObject message = new JSONObject();
			message.put("Topic", topic);
			String messageText = new String(msg.getPayload());
			try{
				message.put("Message", new JSONObject(messageText));
			} catch(JSONException e){
				message.put("Message", messageText);
			}
			message.put("QoS", msg.getQos());
			Set<String> topics = messageListeners.keySet(); 
			for( String registeredTopic : topics ){
				if(Pattern.matches(registeredTopic.replaceAll("/#", ".*").replaceAll("#", ".*").replaceAll("\\\\+", ".+"), topic)){
					Set<IMessageListener> topicListners = messageListeners.get(registeredTopic);
					if(topicListners!=null && !topicListners.isEmpty()){
						for(IMessageListener listner : topicListners){
							listner.receiveMessage(message);
						}
					}
					
				}
			}
			
		}
		
	}

	public EventService() {
		initClient();
	}
	
	public void destroy(){
		try {
			mqttClient.disconnect();
		} catch (MqttException e) {
			logger.warn("Failure while disconecting the mqtt client", e); //$NON-NLS-1$
		}
	}

	private void initClient() {
		mqttConnectOptions = new MqttConnectOptions();
		String serverURI = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_HOST, null);
		String clientId = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_CLIENT_ID, MqttClient.generateClientId());
		if (serverURI == null) {
			//no MQTT message broker host defined
			if (logger.isWarnEnabled()) {
				logger.warn("No MQTT message broker specified in the orion.conf with " + ServerConstants.CONFIG_EVENT_HOST); //$NON-NLS-1$
			}
			return;
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
		
		Properties sslProperties = new Properties();
		String keyType = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_KEY_TYPE, null);
		if(keyType!=null){
			sslProperties.put("com.ibm.ssl.keyStoreType", keyType);
			sslProperties.put("com.ibm.ssl.trustStoreType", keyType);
		}
		String keyPassword = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_KEY_PASSWORD, null);
		if(keyPassword!=null){
			sslProperties.put("com.ibm.ssl.keyStorePassword", keyPassword);
			sslProperties.put("com.ibm.ssl.trustStorePassword", keyPassword);
		}
		String keyStore = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_KEY_STORE, null);
		if(keyStore!=null){
			sslProperties.put("com.ibm.ssl.keyStore", keyStore);
		}
		String trustStore = PreferenceHelper.getString(ServerConstants.CONFIG_EVENT_TRUST_STORE, null);
		if(trustStore!=null){
			sslProperties.put("com.ibm.ssl.trustStore", trustStore);
		}
		mqttConnectOptions.setSSLProperties(sslProperties);

		try {
			mqttClient = new MqttClient(serverURI, clientId, new MemoryPersistence());
			mqttClient.connect(mqttConnectOptions);
			logger.info("MQTT client connected");
		} catch (MqttException e) {
			logger.warn("Failed to initialize MQTT event client", e); //$NON-NLS-1$
		}

		this.callback = new Callback();
		this.mqttClient.setCallback(callback);
	}

	public void publish(String topic, JSONObject message) {
		//nothing to do if we have no MQTT event client configured
		if (mqttClient == null || !mqttClient.isConnected()) {
			return;
		}
		MqttMessage mqttMessage = new MqttMessage(message.toString().getBytes());
		try {
			mqttClient.publish(topic, mqttMessage);
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

	public synchronized void receive(String topic, IMessageListener messageListener) {
		if (logger.isDebugEnabled()) {
			logger.debug("MQTT Receiving topic " + topic + " " + messageListener);
		}
		Set<IMessageListener> topicListeners = messageListeners.get(topic);
		if(topicListeners == null){
			topicListeners = new HashSet<IMessageListener>();
			messageListeners.put(topic, topicListeners);
		}
		if(topicListeners.isEmpty()){
			try {
				if(mqttClient!=null && mqttClient.isConnected()){
					mqttClient.subscribe(topic);
				}
			} catch (MqttException e) {
				logger.warn("Failure subscribing on topic: " + topic, e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		topicListeners.add(messageListener);
	}

	public void stopReceiving(String topic, IMessageListener messageListener) {
		if (logger.isDebugEnabled()) {
			logger.debug("MQTT Stop Receiving topic " + topic + " " + messageListener);
		}
		Set<IMessageListener> topicListeners = messageListeners.get(topic);
		if(topicListeners == null || !mqttClient.isConnected()){
			return;
		}
		topicListeners.remove(messageListener);
		if(topicListeners.isEmpty()){
			try{
				if(mqttClient!=null){
					mqttClient.unsubscribe(topic);
				}
				messageListeners.remove(topic);
			} catch (MqttException e) {
				logger.warn("Failure unsubscribing on topic: " + topic, e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		
	}

}
