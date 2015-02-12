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
import java.util.concurrent.atomic.AtomicBoolean;
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
	
	private static AtomicBoolean reconnectionLock = new AtomicBoolean(false);
	
	private volatile Map<String, Set<IMessageListener>>  messageListeners = new HashMap<String, Set<IMessageListener>>();
	private Callback callback;
	

	/** Quality of Service level of 1 should be the default but just to make it explicit. **/
	private static final int QOS = 1;
	
	private class Callback implements MqttCallback{
		
		public void connectionLost(Throwable e) {
			logger.warn("Connection to MQTT broker was lost. Scheduling job to reconnect.", e);
			new ReconnectMQTTClientJob(EventService.this).schedule();
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
					if (logger.isDebugEnabled()) {
						logger.debug("Pattern matched. Topic: " + topic + " . Registered topic: " + registeredTopic);
					}
					Set<IMessageListener> topicListners = messageListeners.get(registeredTopic);
					if(topicListners!=null && !topicListners.isEmpty()){
						for(IMessageListener listner : topicListners){
							listner.receiveMessage(message);
						}
					}
					else {
						logger.warn("Topic listener could not be found to topic we were subscribed to. Topic: " + registeredTopic);
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
		
		/**
         * If set to false both the client and server will maintain state across restarts of the client, the server and the connection. As state is maintained:
    			Message delivery will be reliable meeting the specified QOS even if the client, server or connection are restarted.
    			The server will treat a subscription as durable. 
    			
    			From Rabbit MQ documentation for the MQTT adapter:
    			Durable (QoS1) subscriptions use durable queues. Whether the queues are auto-deleted is controlled 
    			by the client's clean session flag. Clients with clean sessions use auto-deleted queues, others use non-auto-deleted ones.
         */
		mqttConnectOptions.setCleanSession(false);
		
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
			this.connectMQTTClient();
		} catch (MqttException e) {
			logger.warn("Connection to MQTT broker could not be established. Scheduling job to reconnect.", e);
			new ReconnectMQTTClientJob(this).schedule();
		}

		this.callback = new Callback();
		this.mqttClient.setCallback(callback);
	}
	
	private void connectMQTTClient() throws MqttException {
		mqttClient.connect(mqttConnectOptions);
		logger.info("MQTT client connected.");
	}
	
	public void reconnectMQTTClient() {
		if (!mqttClient.isConnected() && reconnectionLock.compareAndSet(false, true)) {
			this.logger.warn("MQTT client was disconnected. Attempting to reconnect.");
			try {
				new Thread(new Runnable() {
					public void run() {
	
						try {
							connectMQTTClient();
							
							if(mqttClient.isConnected()) {
								logger.info("MQTT client connection reestablished!");
							}
							else {
								logger.warn("Unable to reconnect MQTT client.");
							}
						} catch (MqttException e) {
							logger.error("Could not re-connect the MQTT client. Message: " + e.getMessage() + " Reason code: " + e.getReasonCode());
						}
						finally {
							reconnectionLock.set(false);
						}
					}
				}).start();
			}
			catch (Exception e) {
				this.logger.error("Unexpected exception occurred: " + e.getLocalizedMessage());
				reconnectionLock.set(false);	
			}
		}
	}

	public boolean clientConnected() {
		return mqttClient.isConnected();
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
				if (mqttClient==null) {
					logger.warn("MqttClient was unexpectedly null.");
				}
				else if (!mqttClient.isConnected()) {
					logger.debug("Could not subscribe to topic " + topic + " since MqttClient is disconnected");
				}
				else {
					/** Quality of Service level of 1 should be the default but just to make it explicit.  Why QoS 1? 
					 So the queue is not auto-deleted on disconnection. **/
					mqttClient.subscribe(topic, QOS);
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
