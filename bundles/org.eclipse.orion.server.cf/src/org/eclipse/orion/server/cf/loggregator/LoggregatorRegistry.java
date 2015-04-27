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
package org.eclipse.orion.server.cf.loggregator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 */
public class LoggregatorRegistry {
	private Map<String, LoggregatorListener> logsMap;

	public LoggregatorRegistry() {
		this.logsMap = Collections.synchronizedMap(new HashMap<String, LoggregatorListener>());
	}
	
	public LoggregatorListener getListener(String appId){
		LoggregatorListener listener = logsMap.get(appId);
		if (listener == null){
			listener = new LoggregatorListener();
			logsMap.put(appId, listener);
		}
		return logsMap.get(appId);
	}
}
