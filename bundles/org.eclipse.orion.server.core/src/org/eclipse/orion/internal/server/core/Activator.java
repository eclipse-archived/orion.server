/*******************************************************************************
 * Copyright (c) 2009, 2011 IBM Corporation and others 
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
import java.net.URL;
import java.util.Collection;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.core.tasks.TaskService;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Activator for the server core bundle.
 */
public class Activator implements BundleActivator {

	public static volatile BundleContext bundleContext;

	public static final String PI_SERVER_CORE = "org.eclipse.orion.server.core"; //$NON-NLS-1$
	static Activator singleton;
	ServiceTracker<FrameworkLog, FrameworkLog> logTracker;
	private ServiceRegistration<ITaskService> taskServiceRegistration;

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
			URL root = location.getDataArea(PI_SERVER_CORE);
			// strip off file: prefix from URL
			return new Path(root.toExternalForm().substring(5)).append("tasks"); //$NON-NLS-1$
		} finally {
			context.ungetService(ref);
		}
	}

	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;
		startTaskService();
	}

	private void startTaskService() {
		try {
			IPath taskLocation = getTaskLocation();
			ITaskService service = new TaskService(taskLocation);
			taskServiceRegistration = bundleContext.registerService(ITaskService.class, service, null);
		} catch (IOException e) {
			LogHelper.log(new Status(IStatus.ERROR, PI_SERVER_CORE, "Failed to initialize task service", e)); //$NON-NLS-1$
		}
	}

	public void stop(BundleContext context) throws Exception {
		bundleContext = null;
		stopTaskService();
	}

	private void stopTaskService() {
		ServiceRegistration<ITaskService> reg = taskServiceRegistration;
		taskServiceRegistration = null;
		if (reg != null)
			reg.unregister();
	}
}
