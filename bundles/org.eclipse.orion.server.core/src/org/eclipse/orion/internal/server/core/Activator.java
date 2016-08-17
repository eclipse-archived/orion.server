/*******************************************************************************
 * Copyright (c) 2009, 2016 IBM Corporation and others 
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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.internal.server.core.tasks.TaskService;
import org.eclipse.orion.internal.server.core.workspacepruner.WorkspacePrunerJob;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activator for the server core bundle.
 */
public class Activator implements BundleActivator {

	private static volatile BundleContext bundleContext;

	private static Activator singleton;
	private ServiceTracker<IPreferencesService, IPreferencesService> prefTracker;
	private ServiceRegistration<ITaskService> taskServiceRegistration;
	private ITaskService taskService;
	private IMetaStore metastore;
	private URI rootStoreURI;
	private WorkspacePrunerJob workspacePrunerJob;

	private static final Logger logger = LoggerFactory.getLogger(LogHelper.LOGGER_ID);

	private ServiceTracker<Location, Location> instanceLocationTracker;
	
	public static final String PI_SERVER_CORE = "org.eclipse.orion.server.core"; //$NON-NLS-1$

	public static Activator getDefault() {
		return singleton;
	}

	/**
	 * Returns the currently configured metadata store for this server. This method never returns <code>null</code>.
	 * 
	 * @throws IllegalStateException
	 *             if the server is not properly configured to have an @link {@link IMetaStore}.
	 */
	public synchronized IMetaStore getMetastore() {
		return metastore;
	}

	/**
	 * Returns the preference service, or <code>null</code> if not available.
	 */
	public static IPreferencesService getPreferenceService() {
		// protect against concurrent shutdown
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

	private ServiceTracker<IPreferencesService, IPreferencesService> getPrefTracker() {
		if (prefTracker != null)
			return prefTracker;
		// lazy init if the bundle has been started
		if (bundleContext == null)
			return null;
		prefTracker = new ServiceTracker<IPreferencesService, IPreferencesService>(bundleContext, IPreferencesService.class, null);
		prefTracker.open();
		return prefTracker;
	}

	/**
	 * Returns the root file system location for storing task data. Never returns null.
	 * 
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
		try {
			metastore = new SimpleMetaStore(OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null));
		} catch (CoreException e) {
			String msg = "Cannot initialize MetaStore";
			logger.error(msg, e);
			throw new RuntimeException(msg, e); // $NON-NLS-1$
		}
		bundleContext.registerService(IMetaStore.class, metastore, null);
	}

	@Override
	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;
		registerServices();
		initializeFileSystem();
		initializeMetaStore();
		startWorkspacePrunerJob();
		logger.info("Started Orion server core successfully."); //$NON-NLS-1$
	}

	private void startWorkspacePrunerJob() {
		String workspacePruningEnabled = PreferenceHelper.getString(ServerConstants.CONFIG_WORKSPACEPRUNER_ENABLED, "false").toLowerCase(); //$NON-NLS-1$
		if ("true".equals(workspacePruningEnabled)) { //$NON-NLS-1$
			workspacePrunerJob = new WorkspacePrunerJob();
			/* start the pruning job in one minute */
			workspacePrunerJob.schedule(60000);
		}
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

	@Override
	public void stop(BundleContext context) throws Exception {
		metastore = null;
		stopTaskService();
		if (prefTracker != null) {
			prefTracker.close();
			prefTracker = null;
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
		IPath location = getFileSystemLocation();
		if (location == null) {
			RuntimeException e = new RuntimeException("Unable to compute base file system location");
			logger.error("Failed to initialize server file system", e);
			throw e; // $NON-NLS-1$
		}

		IFileStore rootStore = EFS.getLocalFileSystem().getStore(location);
		try {
			rootStore.mkdir(EFS.NONE, null);
			rootStoreURI = rootStore.toURI();
		} catch (CoreException e) {
			String msg = "Instance location is read only: " + rootStore;
			logger.error(msg, e);
			throw new RuntimeException(msg, e); // $NON-NLS-1$
		}
	}

	private IPath getFileSystemLocation() {
		// Make sure the registry is started so the preferences work correctly.
		// Lots of bundles access the file system location, and some of them start early
		// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=439716#c10
		ensureBundleStarted("org.eclipse.equinox.registry");
		String locationPref = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_USER_CONTENT);

		if (locationPref != null) {
			return new Path(locationPref);
		}
		return getPlatformLocation();
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

	private void ensureBundleStarted(String symbolicName) {
		Bundle bundle = getBundle(symbolicName);
		if (bundle != null) {
			if (bundle.getState() == Bundle.RESOLVED || bundle.getState() == Bundle.STARTING) {
				try {
					bundle.start(Bundle.START_TRANSIENT);
				} catch (BundleException e) {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.core"); //$NON-NLS-1$
					logger.error("Could not start bundle " + symbolicName, e);

				}
			}
		}
	}

	private Bundle getBundle(String symbolicName) {
		for (Bundle bundle : getContext().getBundles()) {
			if (symbolicName.equals(bundle.getSymbolicName())) {
				return bundle;
			}
		}
		return null;
	}

	Location getInstanceLocation() {
		if (instanceLocationTracker == null) {
			Filter filter;
			try {
				filter = bundleContext.createFilter(Location.INSTANCE_FILTER);
			} catch (InvalidSyntaxException e) {
				LogHelper.log(e);
				return null;
			}
			instanceLocationTracker = new ServiceTracker<Location, Location>(bundleContext, filter, null);
			instanceLocationTracker.open();
		}
		return instanceLocationTracker.getService();
	}

	String getProperty(String key) {
		return bundleContext.getProperty(key);
	}
}
