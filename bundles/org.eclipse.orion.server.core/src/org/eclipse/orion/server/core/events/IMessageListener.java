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
package org.eclipse.orion.server.core.events;

import org.json.JSONObject;

/**
 * Listener to be notified when MQTT message arrives. Should be registered with {@link IEventService#receive(String, IMessageListener)}
 *
 */
public interface IMessageListener {
	
	/**
	 * Method called when MQTT message arrives for registered topic
	 * @param message json representation of the message
	 */
	public void receiveMessage(JSONObject message);

}
