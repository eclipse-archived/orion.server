/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		prefServiceTracker.close();
		prefServiceTracker = null;
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
