/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.hosting;

import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.osgi.framework.*;

public class HostingActivator implements BundleActivator {

	public static final String PI_SERVER_HOSTING = "org.eclipse.orion.server.hosting"; //$NON-NLS-1$

	private static BundleContext bundleContext;
	private static HostingActivator singleton;

	private SiteHostingService siteHostingService;
	private ServiceRegistration<ISiteHostingService> siteHostingRegistration;
	private ServiceRegistration<IWebResourceDecorator> hostedStatusDecoratorRegistration;

	static BundleContext getContext() {
		return bundleContext;
	}

	public static HostingActivator getDefault() {
		return singleton;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		singleton = this;
		HostingActivator.bundleContext = bundleContext;
		registerHostingService();
		registerDecorators();
	}

	private void registerHostingService() {
		String jettyPort = System.getProperty("org.eclipse.equinox.http.jetty.http.port", "8080"); //$NON-NLS-1$ //$NON-NLS-2$
		int port = Integer.parseInt(jettyPort);
		SiteHostingConfig config = SiteHostingConfig.getSiteHostingConfig(port, PreferenceHelper.getString(ServerConstants.CONFIG_SITE_VIRTUAL_HOSTS));
		siteHostingService = new SiteHostingService(config);
		siteHostingRegistration = bundleContext.registerService(ISiteHostingService.class, siteHostingService, null);
	}

	private void registerDecorators() {
		hostedStatusDecoratorRegistration = bundleContext.registerService(IWebResourceDecorator.class, new HostedStatusDecorator(), null);
	}

	SiteHostingService getHostingService() {
		return siteHostingService;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		HostingActivator.bundleContext = null;
		unregisterHostingService();
		unregisterDecorators();
		siteHostingService = null;
	}

	private void unregisterHostingService() {
		if (siteHostingRegistration != null) {
			siteHostingRegistration.unregister();
			siteHostingRegistration = null;
		}
	}

	private void unregisterDecorators() {
		if (hostedStatusDecoratorRegistration != null) {
			hostedStatusDecoratorRegistration.unregister();
			hostedStatusDecoratorRegistration = null;
		}
	}

}
