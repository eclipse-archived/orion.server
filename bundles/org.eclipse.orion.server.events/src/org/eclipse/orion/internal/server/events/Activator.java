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
package org.eclipse.orion.internal.server.events;

import org.eclipse.orion.server.core.events.IEventService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Activator for the server events bundle.
 * 
 * @author Anthony Hunter
 */
public class Activator implements BundleActivator {

	public static volatile BundleContext bundleContext;

	static Activator singleton;
	private EventService eventService;

	public static Activator getDefault() {
		return singleton;
	}

	public BundleContext getContext() {
		return bundleContext;
	}

	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;
		registerEventService();
	}

	private void registerEventService() {
		eventService = new EventService();
		if (eventService != null) {
			@SuppressWarnings("unused")
			ServiceRegistration<IEventService> eventServiceRegistration = bundleContext.registerService(IEventService.class, eventService, null);
		}
	}

	public void stop(BundleContext context) throws Exception {
		eventService.destroy();
		eventService = null;
		bundleContext = null;
	}
}