/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.form;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	/**
	 * The symbolic id of this bundle.
	 */
	public static final String PI_FORM_SERVLETS = "org.eclipse.orion.server.authentication.form"; //$NON-NLS-1$

	private static volatile BundleContext bundleContext;

	public static Activator singleton;

	public static Activator getDefault() {
		return singleton;
	}

	public static BundleContext getBundleContext() {
		return bundleContext;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		bundleContext = null;
	}
}
