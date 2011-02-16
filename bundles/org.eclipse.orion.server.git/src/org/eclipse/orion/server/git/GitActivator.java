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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class GitActivator implements BundleActivator {

	// The plug-in ID
	public static final String PI_GIT = "org.eclipse.orion.server.git"; //$NON-NLS-1$

	// The shared instance
	private static GitActivator plugin;

	private BundleContext bundleContext;

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
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
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
