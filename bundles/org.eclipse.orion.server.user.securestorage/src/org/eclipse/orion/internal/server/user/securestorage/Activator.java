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
package org.eclipse.orion.internal.server.user.securestorage;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.useradmin.UserAdmin;

public class Activator implements BundleActivator {

	public static final String PI_USER_SECURESTORAGE = "org.eclipse.orion.server.user.securestorage"; //$NON-NLS-1$
	/**
	 * The system property name for the secure storage master password.
	 */
	public static final String ORION_STORAGE_PASSWORD = "orion.storage.password"; //$NON-NLS-1$
	
	/**
	 * The system property name for the secure storage master password.
	 */
	public static final String ORION_STORAGE_ADMIN_DEFAULT_PASSWORD = "orion.storage.admin.default.password"; //$NON-NLS-1$

	static BundleContext bundleContext;
	private ServiceRegistration<UserAdmin> registerService;

	public static BundleContext getContext() {
		return bundleContext;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext bundleContext) throws Exception {
		Activator.bundleContext = bundleContext;
		registerService = bundleContext.registerService(UserAdmin.class, new SecureStorageCredentialsService(), null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		if (registerService != null)
			registerService.unregister();
		Activator.bundleContext = null;
	}

}
