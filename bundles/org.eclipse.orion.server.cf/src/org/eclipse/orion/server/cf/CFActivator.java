/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.eclipse.orion.server.cf.ds.*;
import org.eclipse.orion.server.cf.sync.FileChangeListener;
import org.eclipse.orion.server.cf.utils.TargetRegistry;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.events.IFileChangeNotificationService;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The activator class controls the plug-in life cycle
 */
public class CFActivator implements BundleActivator {

	// The plug-in ID
	public static final String PI_CF = "org.eclipse.orion.server.cf"; //$NON-NLS-1$

	// The shared instance
	private static CFActivator instance;

	private BundleContext bundleContext;

	private TargetRegistry targetRegistry = new TargetRegistry();

	private ServiceTracker<DeploymentService, IDeploymentService> serviceTracker;

	private IFileChangeNotificationService fileChangeNotificationService;

	private ServiceReference<IFileChangeNotificationService> fileChangeNotificationServiceRef;

	private final Logger logger = LoggerFactory.getLogger(CFActivator.PI_CF); //$NON-NLS-1$

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		instance = this;
		bundleContext = context;

		/* register the deployment service tracker */
		DeploymentServiceTracker customer = new DeploymentServiceTracker(context);
		serviceTracker = new ServiceTracker<DeploymentService, IDeploymentService>(context, DeploymentService.class.getName(), customer);
		serviceTracker.open();

		if (PreferenceHelper.getString(ServerConstants.CONFIG_CF_LIVEUPDATE_ENABLED, "true").equals("true")) {
			fileChangeNotificationServiceRef = context.getServiceReference(IFileChangeNotificationService.class);
			if (fileChangeNotificationServiceRef == null) {

				this.bundleContext.addServiceListener(new ServiceListener() {

					@SuppressWarnings("unchecked")
					@Override
					public void serviceChanged(ServiceEvent event) {
						fileChangeNotificationServiceRef = (ServiceReference<IFileChangeNotificationService>) event.getServiceReference();
						if (fileChangeNotificationServiceRef == null) {
							return;
						}
						startFileChangeNotificationService();
						bundleContext.removeServiceListener(this);
					}
				}, "(objectClass=" + IFileChangeNotificationService.class.getName() + ')');
			} else {
				startFileChangeNotificationService();
			}
		}
	}

	/**
	 * Start the file change notification service that is used by the cf liveupdate feature.
	 */
	private void startFileChangeNotificationService() {
		fileChangeNotificationService = bundleContext.getService(fileChangeNotificationServiceRef);
		if (fileChangeNotificationService == null) {
			logger.warn("No file change notification service available");
			return;
		}
		fileChangeNotificationService.addListener(new FileChangeListener());
		logger.debug("File change notification service started");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		bundleContext = null;
		instance = null;

		serviceTracker.close();
	}

	public static CFActivator getDefault() {
		return instance;
	}

	public BundleContext getContext() {
		return bundleContext;
	}

	public TargetRegistry getTargetRegistry() {
		return targetRegistry;
	}

	public IDeploymentService getDeploymentService() {
		return serviceTracker.getService();
	}

	/**
	 * Returns an HTTPClient instance that is configured to support multiple connections
	 * in different threads. Callers must explicitly release any connections made using this
	 * client.
	 */
	public synchronized HttpClient getHttpClient() {
		//see http://hc.apache.org/httpclient-3.x/threading.html
		MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
		HttpConnectionManagerParams connectionManagerParams = connectionManager.getParams();
		connectionManagerParams.setConnectionTimeout(30000);
		connectionManager.setParams(connectionManagerParams);

		HttpClientParams clientParams = new HttpClientParams();
		clientParams.setConnectionManagerTimeout(300000); // 5 minutes

		return new HttpClient(clientParams, connectionManager);
	}
}
