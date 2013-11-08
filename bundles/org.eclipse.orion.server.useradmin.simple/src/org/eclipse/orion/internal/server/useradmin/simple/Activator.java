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
package org.eclipse.orion.internal.server.useradmin.simple;

import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activator for the Simple User Storage.
 * 
 * @author Anthony Hunter
 */
public class Activator implements BundleActivator {

	public static final String PI_USER_SIMPLE = "org.eclipse.orion.server.useradmin.simple"; //$NON-NLS-1$

	private ServiceRegistration<IOrionCredentialsService> userCredentialsService;
	private ServiceRegistration<IOrionUserProfileService> userProfileService;

	public static Activator singleton;

	public static Activator getDefault() {
		return singleton;
	}

	public void start(BundleContext bundleContext) throws Exception {
		singleton = this;
		String metastore = OrionConfiguration.getMetaStorePreference();

		if (ServerConstants.CONFIG_META_STORE_SIMPLE.equals(metastore)) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			userCredentialsService = bundleContext.registerService(IOrionCredentialsService.class, new SimpleUserCredentialsService(), null);
			logger.debug("Started simple user credentials service."); //$NON-NLS-1$
			userProfileService = bundleContext.registerService(IOrionUserProfileService.class, new SimpleUserProfileService(), null);
			logger.debug("Started simple user profile service."); //$NON-NLS-1$
		}
	}

	public void stop(BundleContext bundleContext) throws Exception {
		if (userCredentialsService != null) {
			userCredentialsService.unregister();
		}
		if (userProfileService != null) {
			userProfileService.unregister();
		}
	}

}
