/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others 
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
import java.net.URL;
import java.util.*;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.hosting.ISiteHostingService;
import org.eclipse.orion.internal.server.servlets.workspace.ProjectParentDecorator;
import org.eclipse.orion.internal.server.servlets.xfer.TransferResourceDecorator;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Activator for the server servlet bundle. Responsible for tracking the HTTP
 * service and registering/unregistering servlets.
 */
public class Activator implements BundleActivator {

	public static volatile BundleContext bundleContext;

	/**
	 * Global flag for enabling debug tracing
	 */
	public static final boolean DEBUG = true;

	public static final String LOCATION_FILE_SERVLET = "/file"; //$NON-NLS-1$
	public static final String LOCATION_PROJECT_SERVLET = "/project"; //$NON-NLS-1$

	public static final String PI_SERVER_SERVLETS = "org.eclipse.orion.server.servlets"; //$NON-NLS-1$
	public static final String PROP_USER_AREA = "org.eclipse.orion.server.core.userArea"; //$NON-NLS-1$

	static Activator singleton;

	private Map<String, URI> aliases = Collections.synchronizedMap(new HashMap<String, URI>());
	private ServiceTracker<IWebResourceDecorator, IWebResourceDecorator> decoratorTracker;
	private ServiceTracker<ISiteHostingService, ISiteHostingService> siteHostingTracker;

	private URI rootStoreURI;
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

	/**
	 * Returns the root file system location for the workspace.
	 */
	public IPath getPlatformLocation() {
		BundleContext context = Activator.getDefault().getContext();
		Collection<ServiceReference<Location>> refs;
		try {
			refs = context.getServiceReferences(Location.class, Location.INSTANCE_FILTER);
		} catch (InvalidSyntaxException e) {
			// we know the instance location filter syntax is valid
			throw new RuntimeException(e);
		}
		if (refs.isEmpty())
			return null;
		ServiceReference<Location> ref = refs.iterator().next();
		Location location = context.getService(ref);
		try {
			if (location == null)
				return null;
			URL root = location.getURL();
			if (root == null)
				return null;
			// strip off file: prefix from URL
			return new Path(root.toExternalForm().substring(5));
		} finally {
			context.ungetService(ref);
		}
	}

	/**
	 * Returns the root location for storing content and metadata on this server.
	 */
	public URI getRootLocationURI() {
		return rootStoreURI;
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
		IPath location = getPlatformLocation();
		if (location == null)
			throw new RuntimeException("Unable to compute base file system location"); //$NON-NLS-1$

		IFileStore rootStore = EFS.getLocalFileSystem().getStore(location);
		try {
			rootStore.mkdir(EFS.NONE, null);
			rootStoreURI = rootStore.toURI();
		} catch (CoreException e) {
			throw new RuntimeException("Instance location is read only: " + rootStore, e); //$NON-NLS-1$
		}

		//initialize user area if not specified
		if (System.getProperty(PROP_USER_AREA) == null) {
			System.setProperty(PROP_USER_AREA, rootStore.getFileStore(new Path(".metadata/.plugins/org.eclipse.orion.server.core/userArea")).toString()); //$NON-NLS-1$
		}
	}

	/**
	 * Registers decorators supplied by servlets in this bundle
	 */
	private void registerDecorators() {
		//adds the import/export locations to representations
		transferDecoratorRegistration = bundleContext.registerService(IWebResourceDecorator.class, new TransferResourceDecorator(), null);
		//adds parent links to representations
		parentDecoratorRegistration = bundleContext.registerService(IWebResourceDecorator.class, new ProjectParentDecorator(), null);
	}

	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;
		initializeFileSystem();
		registerDecorators();
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
		unregisterDecorators();
		bundleContext = null;
	}

	public void unregisterAlias(String alias) {
		aliases.remove(alias);
	}

	private void unregisterDecorators() {
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
