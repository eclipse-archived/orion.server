/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

import java.util.Collection;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The activator class controls the plug-in life cycle
 */
public class GitActivator implements BundleActivator {

	// The plug-in ID
	public static final String PI_GIT = "org.eclipse.orion.server.git"; //$NON-NLS-1$

	// The shared instance
	private static GitActivator plugin;

	private BundleContext bundleContext;

	private ServiceTracker<IPreferencesService, IPreferencesService> prefServiceTracker;
	private ServiceReference<IMetaStore> metastoreServiceReference;

	private IMetaStore metastore;

	/**
	 * The constructor
	 */
	public GitActivator() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext context) throws Exception {
		plugin = this;
		this.bundleContext = context;
		context.registerService(IWebResourceDecorator.class, new GitFileDecorator(), null);
		SshSessionFactory.setInstance(new GitSshSessionFactory());

		prefServiceTracker = new ServiceTracker<IPreferencesService, IPreferencesService>(context, IPreferencesService.class, null);
		prefServiceTracker.open();
	}

	public IPreferencesService getPreferenceService() {
		return prefServiceTracker.getService();
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		prefServiceTracker.close();
		prefServiceTracker = null;
		metastore = null;
		if (metastoreServiceReference != null) {
			bundleContext.ungetService(metastoreServiceReference);
			metastoreServiceReference = null;
		}
		this.bundleContext = null;
		plugin = null;
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static GitActivator getDefault() {
		return plugin;
	}

	public BundleContext getBundleContext() {
		return bundleContext;
	}
}
