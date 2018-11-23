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

import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.http.jetty.JettyConfigurator;
import org.eclipse.equinox.http.jetty.JettyConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * The main application for the Orion server. This application just starts the required server bundles and allows the application to complete asynchronously
 * when closed from the OSGi console.
 */
public class WebApplication implements IApplication {

	private static final String EQUINOX_HTTP_JETTY = "org.eclipse.equinox.http.jetty"; //$NON-NLS-1$
	private static final String EQUINOX_HTTP_REGISTRY = "org.eclipse.equinox.http.registry"; //$NON-NLS-1$
	private static final String JETTY = "jetty"; //$NON-NLS-1$
	private static final String HTTP_PORT = JETTY + '.' + JettyConstants.HTTP_PORT;
	private static final String HTTPS_ENABLED = JETTY + '.' + JettyConstants.HTTPS_ENABLED;
	private static final String HTTPS_PORT = JETTY + '.' + JettyConstants.HTTPS_PORT;
	private static final String SSL_KEYPASSWORD = JETTY + '.' + JettyConstants.SSL_KEYPASSWORD;
	private static final String SSL_KEYSTORE = JETTY + '.' + JettyConstants.SSL_KEYSTORE;

	private static final String SSL_PASSWORD = JETTY + '.' + JettyConstants.SSL_PASSWORD;
	private static final String SSL_PROTOCOL = JETTY + '.' + JettyConstants.SSL_PROTOCOL;
	private IApplicationContext appContext;

	private void ensureBundleStarted(String symbolicName) throws BundleException {
		Bundle bundle = Activator.getBundle(symbolicName);
		if (bundle != null) {
			if (bundle.getState() == Bundle.RESOLVED || bundle.getState() == Bundle.STARTING) {
				bundle.start(Bundle.START_TRANSIENT);
			}
		}
	}

	@Override
	public Object start(IApplicationContext context) throws Exception {
		appContext = context;
		ensureBundleStarted(EQUINOX_HTTP_JETTY);
		ensureBundleStarted(EQUINOX_HTTP_REGISTRY);

		IEclipsePreferences preferences = DefaultScope.INSTANCE.getNode(ServerConstants.PREFERENCE_SCOPE);
		Boolean httpsEnabled = new Boolean(preferences.get(HTTPS_ENABLED, "false")); //$NON-NLS-1$

		Dictionary<String, Object> properties = new Hashtable<String, Object>();
		properties.put(JettyConstants.CONTEXT_SESSIONINACTIVEINTERVAL, new Integer(4 * 60 * 60)); // 4 hours

		String contextPath = preferences.get(ServerConstants.CONFIG_CONTEXT_PATH, null);
		if (contextPath != null) {
			properties.put(JettyConstants.CONTEXT_PATH, contextPath);
		}

		Boolean jettyAccessLogsEnabled = new Boolean(preferences.get(ServerConstants.CONFIG_ACCESS_LOGS_ENABLED, "false"));
		if (jettyAccessLogsEnabled) {
			properties.put(JettyConstants.CUSTOMIZER_CLASS, "org.eclipse.orion.server.jettycustomizer.OrionJettyCustomizer");
		}

		if (httpsEnabled) {
			LogHelper.log(new Status(IStatus.INFO, Activator.PI_JETTY, "Https is enabled", null)); //$NON-NLS-1$

			properties.put(JettyConstants.HTTPS_ENABLED, true);
			properties.put(JettyConstants.HTTPS_PORT,
					new Integer(preferences.get(HTTPS_PORT, System.getProperty("org.eclipse.equinox.http.jetty.https.port", "8443")))); //$NON-NLS-1$//$NON-NLS-2$
			properties.put(JettyConstants.SSL_KEYSTORE, preferences.get(SSL_KEYSTORE, "keystore")); //$NON-NLS-1$

			LogHelper.log(new Status(IStatus.INFO, Activator.PI_JETTY, "Keystore absolute path is " + preferences.get(SSL_KEYSTORE, "keystore"))); //$NON-NLS-1$ //$NON-NLS-2$

			properties.put(JettyConstants.SSL_PASSWORD, preferences.get(SSL_PASSWORD, "password")); //$NON-NLS-1$
			properties.put(JettyConstants.SSL_KEYPASSWORD, preferences.get(SSL_KEYPASSWORD, "password")); //$NON-NLS-1$
			properties.put(JettyConstants.SSL_PROTOCOL, preferences.get(SSL_PROTOCOL, "SSLv3")); //$NON-NLS-1$

			String httpsHost = System.getProperty("org.eclipse.equinox.http.jetty.https.host"); //$NON-NLS-1$
			if (httpsHost != null) {
				properties.put(JettyConstants.HTTPS_HOST, httpsHost);
			}
		}

		String port = null;
		if (!httpsEnabled) {
			properties.put(JettyConstants.HTTP_ENABLED, true);
			port = preferences.get(HTTP_PORT, System.getProperty("org.eclipse.equinox.http.jetty.http.port", "8080"));//$NON-NLS-1$ //$NON-NLS-2$
			properties.put(JettyConstants.HTTP_PORT, new Integer(port));

			String httpHost = System.getProperty("org.eclipse.equinox.http.jetty.http.host"); //$NON-NLS-1$
			if (httpHost != null) {
				properties.put(JettyConstants.HTTP_HOST, httpHost);
			}
		}

		// properties to help us filter orion content
		properties.put("other.info", "org.eclipse.orion"); //$NON-NLS-1$ //$NON-NLS-2$ 

		try {
			JettyConfigurator.startServer("MasterJetty", properties); //$NON-NLS-1$
		} catch (Exception e) {
			throw new Exception("Error starting Jetty on port: " + port, e);
		}

		if (appContext != null)
			appContext.applicationRunning();

		return IApplicationContext.EXIT_ASYNC_RESULT;
	}

	@Override
	public void stop() {
		try {
			JettyConfigurator.stopServer("MasterJetty"); //$NON-NLS-1$
		} catch (Exception e) {
			// best effort
		}

		if (appContext != null)
			appContext.setResult(EXIT_OK, this);
	}

}
