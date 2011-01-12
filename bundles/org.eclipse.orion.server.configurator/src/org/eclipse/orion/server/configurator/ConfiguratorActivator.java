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
package org.eclipse.orion.server.configurator;

import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.AUTHENTICATION_NAME;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.DEFAULT_AUTHENTICATION_NAME;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.HTTPS_PORT;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.HTTP_PORT;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.SSL_KEYPASSWORD;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.SSL_KEYSTORE;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.SSL_PASSWORD;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.SSL_PROTOCOL;

import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.eclipse.orion.server.core.authentication.NoneAuthenticationService;

import org.eclipse.orion.server.configurator.configuration.ConfigurationFormat;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.http.jetty.JettyConfigurator;
import org.eclipse.equinox.http.jetty.JettyConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

public class ConfiguratorActivator implements BundleActivator {

	/**
	 * The symbolic id of this bundle.
	 */
	public static final String PI_CONFIGURATOR = "org.eclipse.orion.server.configurator"; //$NON-NLS-1$
	static ConfiguratorActivator singleton;

	private ServiceTracker<IAuthenticationService, IAuthenticationService> authServiceTracker;
	private ServiceTracker<PackageAdmin, PackageAdmin> packageAdminTracker;

	private Filter getAuthFilter() throws InvalidSyntaxException {
		StringBuilder sb = new StringBuilder("(");
		sb.append(ConfigurationFormat.AUTHENTICATION_NAME);
		sb.append("=");
		sb.append(getAuthName());
		sb.append(")");
		return FrameworkUtil.createFilter(sb.toString());
	}

	private class AuthServiceTracker extends ServiceTracker<IAuthenticationService, IAuthenticationService> {

		public AuthServiceTracker(BundleContext context) throws InvalidSyntaxException {
			super(context, getAuthFilter(), null);
			// TODO: Filters are case sensitive, we should be too
			if (NoneAuthenticationService.AUTH_TYPE.equalsIgnoreCase(getAuthName())) {
				Dictionary<String, String> properties = new Hashtable<String, String>();
				properties.put(ConfigurationFormat.AUTHENTICATION_NAME, getAuthName());
				// TODO: shouldn't we always register the none-auth service?
				context.registerService(IAuthenticationService.class, new NoneAuthenticationService(), properties);
			}
		}

		@Override
		public IAuthenticationService addingService(ServiceReference<IAuthenticationService> reference) {

			if ("true".equals(reference.getProperty("configured")))
				return null;

			IAuthenticationService authService = super.addingService(reference);
			// TODO need to read auth properties from InstanceScope preferences
			authService.configure(new Properties());

			Dictionary dictionary = new Properties();
			dictionary.put("configured", "true");
			context.registerService(IAuthenticationService.class.getName(), authService, dictionary);
			return authService;
		}
	}

	public static ConfiguratorActivator getDefault() {
		return singleton;
	}

	public void start(BundleContext context) throws Exception {
		singleton = this;

		packageAdminTracker = new ServiceTracker<PackageAdmin, PackageAdmin>(context, PackageAdmin.class.getName(), null);
		packageAdminTracker.open();

		authServiceTracker = new AuthServiceTracker(context);
		authServiceTracker.open();

		IEclipsePreferences preferences = (new InstanceScope()).getNode(PI_CONFIGURATOR);
		Boolean httpsEnabled = new Boolean(preferences.get(ConfigurationFormat.HTTPS_ENABLED, "false"));

		Dictionary<String, Object> properties = new Hashtable<String, Object>();
		if (httpsEnabled) {
			LogHelper.log(new Status(IStatus.INFO, PI_CONFIGURATOR, "Https is enabled", null));

			properties.put(JettyConstants.HTTPS_ENABLED, true);
			properties.put(JettyConstants.HTTPS_PORT, new Integer(preferences.get(HTTPS_PORT, System.getProperty("org.eclipse.equinox.http.jetty.https.port", "8443"))));
			properties.put(JettyConstants.SSL_KEYSTORE, preferences.get(SSL_KEYSTORE, "keystore"));

			LogHelper.log(new Status(IStatus.INFO, PI_CONFIGURATOR, "Keystore absolute path is " + preferences.get(SSL_KEYSTORE, "keystore")));

			properties.put(JettyConstants.SSL_PASSWORD, preferences.get(SSL_PASSWORD, "password"));
			properties.put(JettyConstants.SSL_KEYPASSWORD, preferences.get(SSL_KEYPASSWORD, "password"));
			properties.put(JettyConstants.SSL_PROTOCOL, preferences.get(SSL_PROTOCOL, "SSLv3"));
		}

		if (!httpsEnabled) {
			properties.put(JettyConstants.HTTP_ENABLED, true);
			properties.put(JettyConstants.HTTP_PORT, new Integer(preferences.get(HTTP_PORT, System.getProperty("org.eclipse.equinox.http.jetty.http.port", "8080"))));
		}

		JettyConfigurator.startServer("MasterJetty", properties);
	}

	public void stop(BundleContext context) throws Exception {

		JettyConfigurator.stopServer("MasterJetty");

		if (authServiceTracker != null) {
			authServiceTracker.close();
			authServiceTracker = null;
		}

		if (packageAdminTracker != null) {
			packageAdminTracker.close();
			packageAdminTracker = null;
		}
	}

	Bundle getBundle(String symbolicName) {
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

	public IAuthenticationService getAuthService() {
		return authServiceTracker.getService();
	}

	private String getAuthName() {
		IEclipsePreferences preferences = (new InstanceScope()).getNode(PI_CONFIGURATOR);
		return preferences.get(AUTHENTICATION_NAME, DEFAULT_AUTHENTICATION_NAME);
	}
}
