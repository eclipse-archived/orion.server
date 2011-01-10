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
package org.eclipse.e4.webide.server.authentication.basic;

import org.osgi.framework.*;

public class Activator implements BundleActivator {

	/**
	 * Bundle symbolic name.
	 */
	public static final String PI_SERVER_BASICAUTH = FrameworkUtil.getBundle(Activator.class).getSymbolicName();

	private static Activator singleton;
	public static volatile BundleContext bundleContext;

	public static Activator getDefault() {
		return singleton;
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
