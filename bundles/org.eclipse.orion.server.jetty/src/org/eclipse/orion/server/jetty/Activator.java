/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.jetty;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings("deprecation")
public class Activator implements BundleActivator {
	/**
	 * Service reference property indicating if the authentication service has been configured.
	 */
	private static ServiceTracker<PackageAdmin, PackageAdmin> packageAdminTracker;
	/**
	 * The symbolic id of this bundle.
	 */
	public static final String PI_JETTY = "org.eclipse.orion.server.jetty"; //$NON-NLS-1$

	static Bundle getBundle(String symbolicName) {
		PackageAdmin packageAdmin = packageAdminTracker.getService();
		if (packageAdmin == null)
			return null;
		Bundle[] bundles = packageAdmin.getBundles(symbolicName, null);
		if (bundles == null)
			return null;
		// Return the first bundle that is not installed or uninstalled
		for (int i = 0; i < bundles.length; i++) {
			if ((bundles[i].getState() & (Bundle.INSTALLED | Bundle.UNINSTALLED)) == 0) {
				return bundles[i];
			}
		}
		return null;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		packageAdminTracker = new ServiceTracker<PackageAdmin, PackageAdmin>(context, PackageAdmin.class.getName(), null);
		packageAdminTracker.open();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		if (packageAdminTracker != null) {
			packageAdminTracker.close();
			packageAdminTracker = null;
		}
	}
}
