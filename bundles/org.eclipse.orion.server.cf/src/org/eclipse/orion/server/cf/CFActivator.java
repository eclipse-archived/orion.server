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

import java.util.Hashtable;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.eclipse.orion.server.cf.service.*;
import org.eclipse.orion.server.cf.utils.TargetRegistry;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The activator class controls the plug-in life cycle
 */
public class CFActivator implements BundleActivator {
	public static final String PI_CF = "org.eclipse.orion.server.cf"; //$NON-NLS-1$

	private static CFActivator instance;
	private static BundleContext context;

	private TargetRegistry targetRegistry = new TargetRegistry();
	private ServiceTracker<DeploymentService, IDeploymentService> serviceTracker;

	public IDeploymentService getDeploymentService() {
		return serviceTracker.getService();
	}

	private void registerDeploymentService() {

		/* register the service with minimum priority */
		Hashtable<String, Integer> dictionary = new Hashtable<String, Integer>();
		dictionary.put(Constants.SERVICE_RANKING, Integer.MIN_VALUE);
		context.registerService(IDeploymentService.class.getName(), new DeploymentService(), dictionary);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		CFActivator.instance = this;
		CFActivator.context = context;

		/* register the default deployment service */
		registerDeploymentService();

		DeploymentServiceTracker customer = new DeploymentServiceTracker(context);
		serviceTracker = new ServiceTracker<DeploymentService, IDeploymentService>(context, IDeploymentService.class.getName(), customer);
		serviceTracker.open();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		CFActivator.context = null;
		CFActivator.instance = null;
		serviceTracker.close();
	}

	public static CFActivator getDefault() {
		return instance;
	}

	public BundleContext getContext() {
		return context;
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
		//see http://hc.apache.org/httpclient-3.x/threading.html
		MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
		HttpClientParams clientParams = new HttpClientParams();
		clientParams.setConnectionManagerTimeout(300000); // 5 minutes
		return new HttpClient(clientParams, connectionManager);
	}
}
