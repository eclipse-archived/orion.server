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

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.internal.server.servlets.workspace.CompatibilityMetaStore;
import org.eclipse.orion.internal.server.servlets.workspace.ProjectParentDecorator;
import org.eclipse.orion.internal.server.servlets.xfer.TransferResourceDecorator;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Activator for the server servlet bundle. Responsible for tracking required services
 * and registering/unregistering servlets.
 */
@SuppressWarnings("deprecation")
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

	private Map<String, URI> aliases = Collections.synchronizedMap(new HashMap<String, URI>());
	private ServiceTracker<IWebResourceDecorator, IWebResourceDecorator> decoratorTracker;
	private ServiceTracker<ISiteHostingService, ISiteHostingService> siteHostingTracker;

	private ServiceRegistration<IWebResourceDecorator> transferDecoratorRegistration;
	private ServiceRegistration<IWebResourceDecorator> parentDecoratorRegistration;

	private ServiceRegistration<IMetaStore> metastoreRegistration;

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

	private void initializeFileSystem() {
		IFileStore rootStore = OrionConfiguration.getUserHome(null);
		try {
			rootStore.mkdir(EFS.NONE, null);
		} catch (CoreException e) {
			throw new RuntimeException("Instance location is read only: " + rootStore, e); //$NON-NLS-1$
		}

		//initialize user area if not specified
		if (System.getProperty(PROP_USER_AREA) == null) {
			System.setProperty(PROP_USER_AREA, rootStore.getFileStore(new Path(".metadata/.plugins/org.eclipse.orion.server.core/userArea")).toString()); //$NON-NLS-1$
		}
	}

	private void initializeMetaStore() {
		String metastore = OrionConfiguration.getMetaStorePreference();

		if (ServerConstants.CONFIG_META_STORE_SIMPLE.equals(metastore)) {
			try {
				metastoreRegistration = bundleContext.registerService(IMetaStore.class, new SimpleMetaStore(OrionConfiguration.getUserHome(null).toLocalFile(EFS.NONE, null)), null);
			} catch (CoreException e) {
				throw new RuntimeException("Cannot initialize MetaStore", e); //$NON-NLS-1$
			}
		} else {
			//legacy metadata store implementation
			metastoreRegistration = bundleContext.registerService(IMetaStore.class, new CompatibilityMetaStore(), null);
		}
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
		initializeMetaStore();
		initializeFileSystem();
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

	public void unregisterAlias(String alias) {
		aliases.remove(alias);
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
		if (metastoreRegistration != null) {
			metastoreRegistration.unregister();
			metastoreRegistration = null;
		}
	}

}
