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
 * Interface for the Orion event service. It has capabilities the publishing of 
 * MQTT events for changes within the Orion server.
 *  
 * @author Anthony Hunter
 */
public interface IEventService {

	/**
	 * Publish an event to the MQTT message broker.
	 * @param topic The topic for the event.
	 * @param message JSON describing the event.
	 */
	public void publish(String topic, JSONObject message);

}
