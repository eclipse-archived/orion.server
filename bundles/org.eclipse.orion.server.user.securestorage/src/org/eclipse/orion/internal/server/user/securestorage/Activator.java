/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.user.securestorage;

import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {

	public static final String PI_USER_SECURESTORAGE = "org.eclipse.orion.server.user.securestorage"; //$NON-NLS-1$
	/**
	 * The system property name for the secure storage master password.
	 */
	public static final String ORION_STORAGE_PASSWORD = "orion.storage.password"; //$NON-NLS-1$

	static BundleContext bundleContext;
	private ServiceRegistration<IOrionCredentialsService> userCredentialsService;
	private ServiceRegistration<IOrionUserProfileService> userProfileService;

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
		String metastore = OrionConfiguration.getMetaStorePreference();

		if (ServerConstants.CONFIG_META_STORE_LEGACY.equals(metastore)) {
			//Activator.bundleContext = bundleContext;
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			//userCredentialsService = bundleContext.registerService(IOrionCredentialsService.class, new SecureStorageCredentialsService(), null);
			logger.error("Legacy user credentials service is no longer supported."); //$NON-NLS-1$
			//userProfileService = bundleContext.registerService(IOrionUserProfileService.class, new SecureStorageUserProfileService(), null);
			logger.error("Legacy user profile service is no longer supported."); //$NON-NLS-1$
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		if (userCredentialsService != null) {
			userCredentialsService.unregister();
		}
		if (userProfileService != null) {
			userProfileService.unregister();
		}
		Activator.bundleContext = null;
	}

}
