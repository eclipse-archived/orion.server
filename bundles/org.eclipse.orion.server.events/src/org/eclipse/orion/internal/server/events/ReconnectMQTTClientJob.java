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
package org.eclipse.orion.internal.server.events;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.events.IEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* A Job to reconnect to the MQTT Broker every 5 mins while disconnected.
*
* @author Herman Badwal
*/ 
public class ReconnectMQTTClientJob extends Job {

	private IEventService eventService;

	private static final int SECS_BETWEEN_RECONNECTION_ATTEMPTS = 300; //5 mins
	private static final int MILLISECS_IN_A_SEC = 1000;

	private static final Logger logger = LoggerFactory.getLogger("com.ibm.team.scm.orion.server"); //$NON-NLS-1$

	public ReconnectMQTTClientJob(IEventService eventService) {
		super("Reconnect MQTT Client Job"); //$NON-NLS-1$
		this.eventService = eventService;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		if (logger.isDebugEnabled()) {
			logger.debug("Start reconnect MQTT Client Job."); //$NON-NLS-1$
		}

		eventService.reconnectMQTTClient();

		if (!eventService.clientConnected()) {
			// reconnection attempt failed, try again later.
			this.schedule(SECS_BETWEEN_RECONNECTION_ATTEMPTS * MILLISECS_IN_A_SEC);
		}

		return Status.OK_STATUS;
	}

}
