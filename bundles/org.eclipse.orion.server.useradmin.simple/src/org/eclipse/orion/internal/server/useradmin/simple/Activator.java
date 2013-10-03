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

import org.eclipse.orion.server.user.profile.IOrionUserProfileService;
import org.eclipse.orion.server.useradmin.IOrionCredentialsService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Activator for the Simple User Storage.
 * 
 * @author Anthony Hunter
 */
public class Activator implements BundleActivator {

	public static final String PI_USER_SIMPLE = "org.eclipse.orion.server.useradmin.simple"; //$NON-NLS-1$

	private ServiceRegistration<IOrionCredentialsService> registerOrionCredentialsService;
	private ServiceRegistration<IOrionUserProfileService> registerOrionUserProfileService;

	public void start(BundleContext bundleContext) throws Exception {
		//registerOrionCredentialsService = bundleContext.registerService(IOrionCredentialsService.class, new SimpleUserCredentialsService(), null);
		//registerOrionUserProfileService = bundleContext.registerService(IOrionUserProfileService.class, new SimpleUserProfileService(), null);
	}

	public void stop(BundleContext bundleContext) throws Exception {
		if (registerOrionUserProfileService != null) {
			registerOrionUserProfileService.unregister();
		}
		if (registerOrionCredentialsService != null) {
			registerOrionCredentialsService.unregister();
		}
	}

}
