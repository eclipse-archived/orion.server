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
package org.eclipse.orion.internal.server.core;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreV1;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreV2;
import org.eclipse.orion.internal.server.core.tasks.TaskService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activator for the server core bundle.
 */
public class Activator implements BundleActivator {

	static volatile BundleContext bundleContext;

	private static Activator singleton;
	private	ServiceTracker<FrameworkLog, FrameworkLog> logTracker;
	ServiceTracker<IPreferencesService, IPreferencesService> prefTracker;
	private ServiceRegistration<ITaskService> taskServiceRegistration;
	private ITaskService taskService;
	private IMetaStore metastore;
	private URI rootStoreURI;

	public static Activator getDefault() {
		return singleton;
	}

	/**
	 * Returns the framework log, or null if not available
	 */
	public static FrameworkLog getFrameworkLog() {
		//protect against concurrent shutdown
		Activator a = singleton;
		if (a == null)
			return null;
		ServiceTracker<FrameworkLog, FrameworkLog> tracker = a.getLogTracker();
		if (tracker == null)
			return null;
		return tracker.getService();
	}

	/**
	 * Returns the currently configured metadata store for this server. This method never returns <code>null</code>.
	 * @throws IllegalStateException if the server is not properly configured to have an @link {@link IMetaStore}. 
	 */
	public synchronized IMetaStore getMetastore() {
		return metastore;
	}

	/**
	 * Returns the preference service, or <code>null</code> if not available.
	 */
	public static IPreferencesService getPreferenceService() {
		//protect against concurrent shutdown
		Activator a = singleton;
		if (a == null)
			return null;
		ServiceTracker<IPreferencesService, IPreferencesService> tracker = a.getPrefTracker();
		if (tracker == null)
			return null;
		return tracker.getService();
	}
	
	public ITaskService getTaskService() {
		return taskService;
	}
	

	public BundleContext getContext() {
		return bundleContext;
	}

	private ServiceTracker<FrameworkLog, FrameworkLog> getLogTracker() {
		if (logTracker != null)
			return logTracker;
		//lazy init if the bundle has been started
		if (bundleContext == null)
			return null;
		logTracker = new ServiceTracker<FrameworkLog, FrameworkLog>(bundleContext, FrameworkLog.class, null);
		logTracker.open();
		return logTracker;
	}

	private ServiceTracker<IPreferencesService, IPreferencesService> getPrefTracker() {
		if (prefTracker != null)
			return prefTracker;
		//lazy init if the bundle has been started
		if (bundleContext == null)
			return null;
		prefTracker = new ServiceTracker<IPreferencesService, IPreferencesService>(bundleContext, IPreferencesService.class, null);
		prefTracker.open();
		return prefTracker;
	}

	/**
	 * Returns the root file system location for storing task data. Never returns null.
	 * @throws IOException 
	 */
	private IPath getTaskLocation() throws IOException {
		BundleContext context = Activator.getDefault().getContext();
		Collection<ServiceReference<Location>> refs;
		try {
			refs = context.getServiceReferences(Location.class, Location.INSTANCE_FILTER);
		} catch (InvalidSyntaxException e) {
			// we know the instance location filter syntax is valid
			throw new RuntimeException(e);
		}
		if (refs.isEmpty())
			throw new IOException("Framework instance location is undefined"); //$NON-NLS-1$
		ServiceReference<Location> ref = refs.iterator().next();
		Location location = context.getService(ref);
		try {
			if (location == null)
				throw new IOException("Framework instance location is undefined"); //$NON-NLS-1$
			URL root = location.getDataArea(ServerConstants.PI_SERVER_CORE);
			// strip off file: prefix from URL
			return new Path(root.toExternalForm().substring(5)).append("tasks"); //$NON-NLS-1$
		} finally {
			context.ungetService(ref);
		}
	}

	private void initializeMetaStore() {
		String pref = OrionConfiguration.getMetaStorePreference();
		if (ServerConstants.CONFIG_META_STORE_SIMPLE_V2.equals(pref)) {
			try {
				metastore =  new SimpleMetaStoreV2(OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null));
			} catch (CoreException e) {
				throw new RuntimeException("Cannot initialize MetaStore", e); //$NON-NLS-1$
			}
		} else if (ServerConstants.CONFIG_META_STORE_SIMPLE.equals(pref)) {
			try {
				metastore = new SimpleMetaStoreV1(OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null));
			} catch (CoreException e) {
				throw new RuntimeException("Cannot initialize MetaStore", e); //$NON-NLS-1$
			}
		} else {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error("Preference orion.core.metastore was not supplied and legacy files exist, see https://wiki.eclipse.org/Orion/Metadata_migration to migrate to the current version");
			return;
		}
		bundleContext.registerService(IMetaStore.class, metastore, null);
	}
	
	
	/*(non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;
		registerServices();
		initializeFileSystem();
		initializeMetaStore();
	}

	private void registerServices() {
		try {
			IPath taskLocation = getTaskLocation();
			taskService = new TaskService(taskLocation);
			taskServiceRegistration = bundleContext.registerService(ITaskService.class, taskService, null);
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Failed to initialize task service", e)); //$NON-NLS-1$
		}
	}

	/*(non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		metastore = null;
		stopTaskService();
		if (prefTracker != null) {
			prefTracker.close();
			prefTracker = null;
		}
		if (logTracker != null) {
			logTracker.close();
			logTracker = null;
		}
		bundleContext = null;
	}

	/**
	 * Returns the root file system location the OSGi instance area.
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

	private void initializeFileSystem() {
		String locationPref = System.getProperty(ServerConstants.CONFIG_FILE_USER_CONTENT + ".test");
		if (locationPref != null) {
			locationPref = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_USER_CONTENT);
		}

		IPath location = null;
		if (locationPref != null) {
			location = new Path(locationPref);
		}
		if (location == null) {
			location = getPlatformLocation();
		}
		if (location == null)
			throw new RuntimeException("Unable to compute base file system location"); //$NON-NLS-1$

		IFileStore rootStore = EFS.getLocalFileSystem().getStore(location);
		try {
			rootStore.mkdir(EFS.NONE, null);
			rootStoreURI = rootStore.toURI();
		} catch (CoreException e) {
			throw new RuntimeException("Instance location is read only: " + rootStore, e); //$NON-NLS-1$
		}
	}

	private void stopTaskService() {
		ServiceRegistration<ITaskService> reg = taskServiceRegistration;
		taskServiceRegistration = null;
		taskService = null;
		if (reg != null)
			reg.unregister();
	}

	/**
	 * Returns the root location for storing content and metadata on this server.
	 */
	public URI getRootLocationURI() {
		return rootStoreURI;
	}
}
