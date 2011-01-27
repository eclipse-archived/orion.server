/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin;

import org.eclipse.orion.server.core.authentication.IAuthenticationService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

public class UserAdminActivator implements BundleActivator {

	/**
	 * The symbolic id of this bundle.
	 */
	public static final String PI_USERADMIN = "org.eclipse.orion.server.core.useradmin"; //$NON-NLS-1$

	private static UserAdminActivator singleton;
	private BundleContext bundleContext;

	public BundleContext getBundleContext() {
		return bundleContext;
	}

	public static UserAdminActivator getDefault() {
		return singleton;
	}

	private ServiceTracker<IAuthenticationService, IAuthenticationService> authServiceTracker;

	/**
	 * If an {@link OrionUserAdmin} of this name exists it will be returned as default by {@link #getUserStore()}
	 */
	public static final String eclipseWebUsrAdminName = "Orion";

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext bundleContext) throws Exception {
		singleton = this;
		this.bundleContext = bundleContext;

		Filter authFilter = FrameworkUtil.createFilter("(&(" + Constants.OBJECTCLASS + "=" + IAuthenticationService.class.getName() + ")(configured=true))");

		authServiceTracker = new ServiceTracker<IAuthenticationService, IAuthenticationService>(bundleContext, authFilter, null);
		authServiceTracker.open();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		if (authServiceTracker != null) {
			authServiceTracker.close();
			authServiceTracker = null;
		}

		this.bundleContext = null;
	}

	public IAuthenticationService getAuthenticationService() {
		return authServiceTracker.getService();
	}

}
