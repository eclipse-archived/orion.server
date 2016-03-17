/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.events;

import org.json.JSONObject;

/**
 * Interface for the Orion messaging service, which publishes/receives messages to/from a remote message broker.
 * 
 * @author Anthony Hunter, Malgorzata Janczarska
 */
public interface IMessagingService {

	/**
	 * Publish a message to the remote message broker.
	 * 
	 * @param topic
	 *            The topic for the message.
	 * @param message
	 *            JSON describing the message.
	 */
	public void publish(String topic, JSONObject message);

	/**
	 * Register a listener that will receive messages for the given topic
	 * 
	 * @param topic
	 *            topic The topic for the message.
	 * @param messageListener
	 */
	public void receive(String topic, IMessageListener messageListener);

	/**
	 * Unregister the listener from the topic
	 * 
	 * @param topic
	 * @param messageListener
	 */
	public void stopReceiving(String topic, IMessageListener messageListener);

	void reconnectMessagingClient();

	boolean clientConnected();
}
