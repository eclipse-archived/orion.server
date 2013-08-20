/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.server.logs;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class LogServiceTracker implements
		ServiceTrackerCustomizer<LogService, ILogService> {

	private final BundleContext context;

	public LogServiceTracker(BundleContext context) {
		this.context = context;
	}

	@Override
	public ILogService addingService(ServiceReference<LogService> reference) {
		ILogService logService = context.getService(reference);
		return logService;
	}

	@Override
	public void modifiedService(ServiceReference<LogService> reference,
			ILogService service) {
		/* replace the service */
		removedService(reference, service);
		addingService(reference);
	}

	@Override
	public void removedService(ServiceReference<LogService> reference,
			ILogService service) {
		context.ungetService(reference);
	}
}