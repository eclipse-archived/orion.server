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
package org.eclipse.orion.server.cf.loggregator;

import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.json.JSONArray;

public class LoggregatorListener {

	private SortedSet<LoggregatorMessage.Message> messages;
	
	private long lastTimestamp = -1;

	public void add(LoggregatorMessage.Message msg) {
		if (messages == null) {
			Comparator<LoggregatorMessage.Message> comparator = new Comparator<LoggregatorMessage.Message>() {
				public int compare(LoggregatorMessage.Message o1, LoggregatorMessage.Message o2) {
					if (o1.getTimestamp() < o2.getTimestamp())
						return -1;
					else if (o1.getTimestamp() > o2.getTimestamp())
						return 1;
					else
						return 0;
				}
			};
			messages = new TreeSet<LoggregatorMessage.Message>(comparator);
		}

		messages.add(msg);
	}

	/**
	 * @return the lastTimestamp
	 */
	public long getLastTimestamp() {
		return lastTimestamp;
	}

	public JSONArray getMessagesJSON() {
		JSONArray messagesJSON = new JSONArray();
		if (messages != null){
			for (Iterator<LoggregatorMessage.Message> iterator = messages.iterator(); iterator.hasNext();) {
				LoggregatorMessage.Message loggregatorMessage = iterator.next();
				String message = loggregatorMessage.getMessage().toStringUtf8();
				messagesJSON.put(message);
				lastTimestamp = loggregatorMessage.getTimestamp();
			}
		}
		return messagesJSON;
	}

	public String getString() {
		StringBuffer buff = new StringBuffer();
		if (messages != null)
			for (Iterator<LoggregatorMessage.Message> iterator = messages.iterator(); iterator.hasNext();) {
				String message = iterator.next().getMessage().toStringUtf8();
				buff.append(message).append("\n");
			}
		return buff.toString();
	}
}
