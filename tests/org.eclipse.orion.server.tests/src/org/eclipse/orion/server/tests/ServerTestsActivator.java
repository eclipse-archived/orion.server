/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests;

import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.jetty.WebApplication;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings("deprecation")
// PackageAdmin is deprecated but is not being removed during Luna
public class ServerTestsActivator implements BundleActivator {
	private static final String EQUINOX_HTTP_JETTY = "org.eclipse.equinox.http.jetty"; //$NON-NLS-1$
	private static final String EQUINOX_HTTP_REGISTRY = "org.eclipse.equinox.http.registry"; //$NON-NLS-1$
	public static final String PI_TESTS = "org.eclipse.orion.server.tests";

	public static BundleContext bundleContext;
	private static ServiceTracker<HttpService, HttpService> httpServiceTracker;
	private static ServiceTracker<PackageAdmin, PackageAdmin> packageAdminTracker;

	private static boolean initialized = false;
	private static String serverHost = null;
	private static int serverPort = 0;
	private static WebApplication webapp;

	public static BundleContext getContext() {
		return bundleContext;
	}

	public static String getServerLocation() {
		if (!initialized) {
			try {
				//make sure the http registry is started
				ensureBundleStarted(EQUINOX_HTTP_JETTY);
				ensureBundleStarted(EQUINOX_HTTP_REGISTRY);
				//get the webide bundle started via lazy activation.
				org.eclipse.orion.server.authentication.Activator.getDefault();
				Activator.getDefault();
				webapp = new WebApplication();
				webapp.start(null);
				initialize();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return "http://" + serverHost + ':' + String.valueOf(serverPort) + "/";// + "cc/";
	}

	private static void initialize() throws Exception {
		ServiceReference<HttpService> reference = httpServiceTracker.getServiceReference();
		String port = (String) reference.getProperty("http.port"); //$NON-NLS-1$
		serverHost = "localhost"; //$NON-NLS-1$
		serverPort = Integer.parseInt(port);
		initialized = true;
	}

	public void start(BundleContext context) throws Exception {
		bundleContext = context;
		httpServiceTracker = new ServiceTracker<HttpService, HttpService>(context, HttpService.class, null);
		httpServiceTracker.open();

		packageAdminTracker = new ServiceTracker<PackageAdmin, PackageAdmin>(context, PackageAdmin.class.getName(), null);
		packageAdminTracker.open();
	}

	public void stop(BundleContext context) throws Exception {
		if (webapp != null)
			webapp.stop();

		if (httpServiceTracker != null)
			httpServiceTracker.close();
		if (packageAdminTracker != null)
			packageAdminTracker.close();

		httpServiceTracker = null;
		packageAdminTracker = null;
		bundleContext = null;
	}

	static private Bundle getBundle(String symbolicName) {
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

	static private void ensureBundleStarted(String name) throws BundleException {
		Bundle bundle = getBundle(name);
		if (bundle != null) {
			if (bundle.getState() == Bundle.RESOLVED || bundle.getState() == Bundle.STARTING) {
				bundle.start(Bundle.START_TRANSIENT);
			}
		}
	}
}
