/*******************************************************************************
 * Copyright (c) 2009, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets;

import java.util.Collection;

import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.internal.server.servlets.workspace.ProjectParentDecorator;
import org.eclipse.orion.internal.server.servlets.xfer.TransferResourceDecorator;
import org.eclipse.orion.server.core.IWebResourceDecorator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Activator for the server servlet bundle. Responsible for tracking required services
 * and registering/unregistering servlets.
 */
public class Activator implements BundleActivator {

	public static volatile BundleContext bundleContext;

	/**
	 * Global flag for enabling debug tracing
	 */
	public static final boolean DEBUG = true;

	public static final String LOCATION_FILE_SERVLET = "/file"; //$NON-NLS-1$
	public static final String LOCATION_WORKSPACE_SERVLET = "/workspace"; //$NON-NLS-1$

	public static final String PI_SERVER_SERVLETS = "org.eclipse.orion.server.servlets"; //$NON-NLS-1$
	public static final String PROP_USER_AREA = "org.eclipse.orion.server.core.userArea"; //$NON-NLS-1$

	static Activator singleton;

	private ServiceTracker<IWebResourceDecorator, IWebResourceDecorator> decoratorTracker;
	private ServiceTracker<ISiteHostingService, ISiteHostingService> siteHostingTracker;

	private ServiceRegistration<IWebResourceDecorator> transferDecoratorRegistration;
	private ServiceRegistration<IWebResourceDecorator> parentDecoratorRegistration;

	public static Activator getDefault() {
		return singleton;
	}

	public BundleContext getContext() {
		return bundleContext;
	}

	private synchronized ServiceTracker<IWebResourceDecorator, IWebResourceDecorator> getDecoratorTracker() {
		if (decoratorTracker == null) {
			decoratorTracker = new ServiceTracker<IWebResourceDecorator, IWebResourceDecorator>(bundleContext, IWebResourceDecorator.class, null);
			decoratorTracker.open();
		}
		return decoratorTracker;
	}

	private synchronized ServiceTracker<ISiteHostingService, ISiteHostingService> getSiteHostingTracker() {
		if (siteHostingTracker == null) {
			siteHostingTracker = new ServiceTracker<ISiteHostingService, ISiteHostingService>(bundleContext, ISiteHostingService.class, null);
			siteHostingTracker.open();
		}
		return siteHostingTracker;
	}

	public Collection<IWebResourceDecorator> getWebResourceDecorators() {
		ServiceTracker<IWebResourceDecorator, IWebResourceDecorator> tracker = getDecoratorTracker();
		return tracker.getTracked().values();
	}

	public ISiteHostingService getSiteHostingService() {
		ServiceTracker<ISiteHostingService, ISiteHostingService> tracker = getSiteHostingTracker();
		Collection<ISiteHostingService> hostingServices = tracker.getTracked().values();
		return hostingServices.size() == 0 ? null : hostingServices.iterator().next();
	}

	/**
	 * Registers services supplied by this bundle
	 */
	private void registerServices() {
		//adds the import/export locations to representations
		transferDecoratorRegistration = bundleContext.registerService(IWebResourceDecorator.class, new TransferResourceDecorator(), null);
		//adds parent links to representations
		parentDecoratorRegistration = bundleContext.registerService(IWebResourceDecorator.class, new ProjectParentDecorator(), null);
	}

	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;
		registerServices();
	}

	public void stop(BundleContext context) throws Exception {
		if (decoratorTracker != null) {
			decoratorTracker.close();
			decoratorTracker = null;
		}
		if (siteHostingTracker != null) {
			siteHostingTracker.close();
			siteHostingTracker = null;
		}
		unregisterServices();
		bundleContext = null;
	}

	private void unregisterServices() {
		if (transferDecoratorRegistration != null) {
			transferDecoratorRegistration.unregister();
			transferDecoratorRegistration = null;
		}
		if (parentDecoratorRegistration != null) {
			parentDecoratorRegistration.unregister();
			parentDecoratorRegistration = null;
		}
	}
}
