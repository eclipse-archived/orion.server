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
import org.eclipse.orion.server.core.events.IMessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* A Job to reconnect to the message Broker every 30s while disconnected.
*
* @author Herman Badwal
*/ 
public class ReconnectMessagingClientJob extends Job {

	private IMessagingService messagingService;
	private static final int MS_BETWEEN_RECONNECTION_ATTEMPTS = 30 * 1000;
	private static final Logger logger = LoggerFactory.getLogger("com.ibm.team.scm.orion.server"); //$NON-NLS-1$

	public ReconnectMessagingClientJob(IMessagingService messagingService) {
		super("Reconnect Messaging Client Job"); //$NON-NLS-1$
		this.messagingService = messagingService;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		if (logger.isDebugEnabled()) {
			logger.debug("Start Reconnect Messaging Client Job."); //$NON-NLS-1$
		}

		messagingService.reconnectMessagingClient();
		if (!messagingService.clientConnected()) {
			/* reconnection attempt failed, try again later */
			this.schedule(MS_BETWEEN_RECONNECTION_ATTEMPTS);
		}

		return Status.OK_STATUS;
	}
}
