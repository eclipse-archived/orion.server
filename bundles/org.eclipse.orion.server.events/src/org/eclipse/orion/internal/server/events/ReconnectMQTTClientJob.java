package org.eclipse.orion.internal.server.events;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.events.IEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
