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
package org.eclipse.orion.server.tests;

import java.util.Dictionary;
import java.util.Hashtable;
import org.eclipse.equinox.http.jetty.JettyConfigurator;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.configurator.ConfiguratorActivator;
import org.osgi.framework.*;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;

public class ServerTestsActivator implements BundleActivator {

	public static BundleContext bundleContext;
	private static ServiceTracker httpServiceTracker;
	private static boolean initialized = false;
	private static String serverHost = null;
	private static int serverPort = 0;

	public static BundleContext getContext() {
		return bundleContext;
	}

	public static String getServerLocation() {
		if (!initialized) {
			try {
				initialize();
				//get the webide bundle started via lazy activation.
				org.eclipse.orion.server.authentication.basic.Activator.getDefault();
				Activator.getDefault();
				org.eclipse.orion.internal.server.useradmin.xml.Activator.getContext();
				ConfiguratorActivator.getDefault();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return "http://" + serverHost + ':' + String.valueOf(serverPort);
	}

	private static void initialize() throws Exception {
		// ensure that the http stuff is started
		Dictionary<String, Object> d = new Hashtable<String, Object>();
		d.put("http.port", new Integer(0)); //$NON-NLS-1$

		JettyConfigurator.startServer("webide server tests", d);

		ServiceReference reference = httpServiceTracker.getServiceReference();

		String port = (String) reference.getProperty("http.port"); //$NON-NLS-1$
		serverHost = "localhost"; //$NON-NLS-1$
		serverPort = Integer.parseInt(port);
		initialized = true;
	}

	public void start(BundleContext context) throws Exception {
		bundleContext = context;
		httpServiceTracker = new ServiceTracker(context, HttpService.class.getName(), null);
		httpServiceTracker.open();
	}

	public void stop(BundleContext context) throws Exception {
		if (httpServiceTracker != null)
			httpServiceTracker.close();

		httpServiceTracker = null;
		bundleContext = null;
	}
}
