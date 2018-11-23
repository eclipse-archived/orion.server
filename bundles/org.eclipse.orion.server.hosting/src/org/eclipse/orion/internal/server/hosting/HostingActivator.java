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

import java.util.Collection;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.osgi.framework.*;

public class HostingActivator implements BundleActivator {

	public static final String PI_SERVER_HOSTING = "org.eclipse.orion.server.hosting"; //$NON-NLS-1$

	private static BundleContext bundleContext;
	private static HostingActivator singleton;

	private IMetaStore metastore;
	private SiteHostingService siteHostingService;
	private ServiceRegistration<ISiteHostingService> siteHostingRegistration;
	private ServiceReference<IMetaStore> metastoreServiceReference;
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

	/**
	 * Returns the currently configured metadata store for this server. This method never returns <code>null</code>.
	 * @throws IllegalStateException if the server is not properly configured to have a metastore. 
	 */
	public synchronized IMetaStore getMetastore() {
		if (metastore == null) {
			//todo orion configuration should specify which metadata store to use
			String filter = null;
			Collection<ServiceReference<IMetaStore>> services;
			try {
				services = bundleContext.getServiceReferences(IMetaStore.class, filter);
			} catch (InvalidSyntaxException e) {
				//can only happen if our filter is malformed, which it should never be
				throw new RuntimeException(e);
			}
			if (services.size() == 1) {
				metastoreServiceReference = services.iterator().next();
				metastore = bundleContext.getService(metastoreServiceReference);
			}
			if (metastore == null) {
				//if we still don't have a store then something is wrong with server configuration
				final String msg = "Invalid server configuration. Failed to initialize a metadata store"; //$NON-NLS-1$
				throw new IllegalStateException(msg);
			}
		}
		return metastore;
	}

	private void registerHostingService() {
		SiteHostingConfig config = SiteHostingConfig.getSiteHostingConfig(PreferenceHelper.getString(ServerConstants.CONFIG_SITE_VIRTUAL_HOSTS));
		siteHostingService = new SiteHostingService(config);
		siteHostingRegistration = bundleContext.registerService(ISiteHostingService.class, siteHostingService, null);
	}

	private void registerDecorators() {
		hostedStatusDecoratorRegistration = bundleContext.registerService(IWebResourceDecorator.class, new HostedStatusDecorator(), null);
	}

	public SiteHostingService getHostingService() {
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
