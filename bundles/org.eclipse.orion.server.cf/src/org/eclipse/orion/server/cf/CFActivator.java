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
package org.eclipse.orion.server.cf;

import java.security.Security;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.eclipse.orion.server.cf.utils.TargetRegistry;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class CFActivator implements BundleActivator {

	// The plug-in ID
	public static final String PI_CF = "org.eclipse.orion.server.cf"; //$NON-NLS-1$

	// The shared instance
	private static CFActivator plugin;

	private BundleContext bundleContext;

	private TargetRegistry targetRegistry = new TargetRegistry();

	public CFActivator() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		plugin = this;
		this.bundleContext = context;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		this.bundleContext = null;
		plugin = null;
	}

	public static CFActivator getDefault() {
		return plugin;
	}

	public BundleContext getContext() {
		return bundleContext;
	}

	public TargetRegistry getTargetRegistry() {
		return targetRegistry;
	}

	/**
	 * Returns an HTTPClient instance that is configured to support multiple connections
	 * in different threads. Callers must explicitly release any connections made using this
	 * client.
	 */
	public synchronized HttpClient getHttpClient() {
		//TODO this is temporary until proper configuration is implemented on servers
		Security.setProperty("ssl.SocketFactory.provider", "com.ibm.jsse2.SSLSocketFactoryImpl");
		Security.setProperty("ssl.ServerSocketFactory.provider", "com.ibm.jsse2.SSLServerSocketFactoryImpl");
		//see http://hc.apache.org/httpclient-3.x/threading.html
		MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
		return new HttpClient(connectionManager);
	}
}
