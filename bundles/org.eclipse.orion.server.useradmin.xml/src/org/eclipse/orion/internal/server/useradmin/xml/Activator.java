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
package org.eclipse.orion.internal.server.useradmin.xml;

import java.net.URL;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.useradmin.UserAdmin;

public class Activator implements BundleActivator {

	public static final String PI_USERADMIN_XML = "org.eclipse.orion.server.core.useradmin.xml"; //$NON-NLS-1$

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
		URL entry = Activator.bundleContext.getBundle().getEntry("data/users.xml");
		registerService = bundleContext.registerService(UserAdmin.class, new XmlUserAdmin(entry), null);
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
