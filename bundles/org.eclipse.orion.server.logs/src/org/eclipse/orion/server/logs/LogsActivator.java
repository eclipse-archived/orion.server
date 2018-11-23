/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.logs;

import org.eclipse.orion.server.core.PreferenceHelper;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class LogsActivator implements BundleActivator {
	private static BundleContext context;
	private static LogsActivator instance;

	private ServiceTracker<LogService, ILogService> serviceTracker;

	static BundleContext getContext() {
		return context;
	}

	public static LogsActivator getDefault() {
		return instance;
	}

	/**
	 * @return LogService implementation or null if none present.
	 */
	public ILogService getLogService() {
		return serviceTracker.getService();
	}

	/**
	 * Registers the log service if If there's a log provider property flag.
	 */
	private void registerLogService() {
		/*
		 * TODO: The log provider service should be enabled by default for all
		 * /* privileged users (e. g. administrator). Currently, all logged
		 * users have /* access if the flag is present and set to true.
		 */
		boolean enabled = Boolean.parseBoolean(PreferenceHelper
				.getString(LogConstants.CONFIG_FILE_LOG_PROVIDER_ENABLED));

		if (!enabled)
			return;

		/* register the service */
		context.registerService(ILogService.class.getName(), new LogService(),
				null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	@Override
	public void start(BundleContext bundleContext) throws Exception {
		LogsActivator.context = bundleContext;
		LogsActivator.instance = this;

		/* register the default log service */
		registerLogService();

		LogServiceTracker customer = new LogServiceTracker(context);
		serviceTracker = new ServiceTracker<LogService, ILogService>(context,
				ILogService.class.getName(), customer);

		serviceTracker.open();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		LogsActivator.context = null;
		LogsActivator.instance = null;
		serviceTracker.close();
	}
}
