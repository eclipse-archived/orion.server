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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.cf.CFActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class LoggregatorRegistry {

	private final Logger logger = LoggerFactory.getLogger(CFActivator.PI_CF); //$NON-NLS-1$

	private static final long CLEANER_DELAY = TimeUnit.SECONDS.toMillis(5);
	private static final long LOG_TTL = TimeUnit.MINUTES.toMillis(10);

	private Map<String, LoggregatorListener> logsMap;
	private Cleaner cleaner = new Cleaner();

	public LoggregatorRegistry() {
		this.logsMap = Collections.synchronizedMap(new HashMap<String, LoggregatorListener>());
	}

	public LoggregatorListener getListener(String appId) {
		logger.debug("LoggregatorRegistry: Getting logger for app " + appId);
		LoggregatorListener listener = logsMap.get(appId);
		if (listener == null) {
			listener = new LoggregatorListener();
			logsMap.put(appId, listener);
		}
		
		cleaner.schedule(CLEANER_DELAY);
		return listener;
	}

	class Cleaner extends Job {
		/**
		 * @param name
		 */
		public Cleaner() {
			super("Loggregator Registry Cleaner");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
		 */
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			logger.debug("LoggregatorRegistry: Running cleaner");
			if (logsMap.entrySet().size() == 0){
				logger.debug("LoggregatorRegistry: Stopping cleaner");
				return Status.OK_STATUS;
			}
			
			Iterator<Map.Entry<String, LoggregatorListener>> iter = logsMap.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<String, LoggregatorListener> entry = iter.next();
				if (System.currentTimeMillis() - entry.getValue().getLastAccess() > LOG_TTL) {
					logger.debug("LoggregatorRegistry: Removing logger " + entry.getKey());
					iter.remove();
				}
			}

			this.schedule(CLEANER_DELAY);
			return Status.OK_STATUS;
		}
	}
}
