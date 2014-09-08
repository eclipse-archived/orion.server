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

import java.util.*;
import org.json.JSONArray;

public class LoggregatorListener {

	private List<String> messages;

	public void add(String msg) {
		if (messages == null)
			messages = new ArrayList<String>();
		messages.add(msg);
	}

	public JSONArray getMessagesJSON() {
		JSONArray messagesJSON = new JSONArray();
		if (messages != null)
			for (Iterator<String> iterator = messages.iterator(); iterator.hasNext();) {
				messagesJSON.put(iterator.next());
			}
		return messagesJSON;
	}
}
