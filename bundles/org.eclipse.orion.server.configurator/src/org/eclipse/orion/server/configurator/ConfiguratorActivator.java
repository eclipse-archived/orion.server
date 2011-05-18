/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.configurator;

import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.DEFAULT_AUTHENTICATION_NAME;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.HTTPS_PORT;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.HTTP_PORT;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.SSL_KEYPASSWORD;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.SSL_KEYSTORE;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.SSL_PASSWORD;
import static org.eclipse.orion.server.configurator.configuration.ConfigurationFormat.SSL_PROTOCOL;

import java.util.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.http.jetty.JettyConfigurator;
import org.eclipse.equinox.http.jetty.JettyConstants;
import org.eclipse.orion.server.configurator.configuration.ConfigurationFormat;
import org.eclipse.orion.server.core.*;
import org.eclipse.orion.server.core.authentication.IAuthenticationService;
import org.eclipse.orion.server.core.authentication.NoneAuthenticationService;
import org.osgi.framework.*;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

public class ConfiguratorActivator implements BundleActivator {

	/**
	 * The symbolic id of this bundle.
	 */
	public static final String PI_CONFIGURATOR = "org.eclipse.orion.server.configurator"; //$NON-NLS-1$
	/**
	 * Service reference property indicating if the authentication service has been configured.
	 */
	static final String PROP_CONFIGURED = "configured"; //$NON-NLS-1$
	static ConfiguratorActivator singleton;

	private ServiceTracker<IAuthenticationService, IAuthenticationService> authServiceTracker;
	private ServiceTracker<PackageAdmin, PackageAdmin> packageAdminTracker;

	Filter getAuthFilter() throws InvalidSyntaxException {
		StringBuilder sb = new StringBuilder("("); //$NON-NLS-1$
		sb.append(ServerConstants.CONFIG_AUTH_NAME);
		sb.append('=');
		sb.append(getAuthName());
		sb.append(')');
		return FrameworkUtil.createFilter(sb.toString());
	}

	private class AuthServiceTracker extends ServiceTracker<IAuthenticationService, IAuthenticationService> {

		public AuthServiceTracker(BundleContext context) throws InvalidSyntaxException {
			super(context, getAuthFilter(), null);
			// TODO: Filters are case sensitive, we should be too
			if (NoneAuthenticationService.AUTH_TYPE.equalsIgnoreCase(getAuthName())) {
				Dictionary<String, String> properties = new Hashtable<String, String>();
				properties.put(ServerConstants.CONFIG_AUTH_NAME, getAuthName());
				// TODO: shouldn't we always register the none-auth service?
				context.registerService(IAuthenticationService.class, new NoneAuthenticationService(), properties);
			}
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		@Override
		public IAuthenticationService addingService(ServiceReference<IAuthenticationService> reference) {
			if ("true".equals(reference.getProperty(PROP_CONFIGURED))) //$NON-NLS-1$
				return null;

			IAuthenticationService authService = super.addingService(reference);
			// TODO need to read auth properties from InstanceScope preferences
			authService.configure(new Properties());

			Dictionary dictionary = new Properties();
			dictionary.put(PROP_CONFIGURED, "true"); //$NON-NLS-1$
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

		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(PI_CONFIGURATOR);
		Boolean httpsEnabled = new Boolean(preferences.get(ConfigurationFormat.HTTPS_ENABLED, "false")); //$NON-NLS-1$

		Dictionary<String, Object> properties = new Hashtable<String, Object>();
		if (httpsEnabled) {
			LogHelper.log(new Status(IStatus.INFO, PI_CONFIGURATOR, "Https is enabled", null)); //$NON-NLS-1$

			properties.put(JettyConstants.HTTPS_ENABLED, true);
			properties.put(JettyConstants.HTTPS_PORT, new Integer(preferences.get(HTTPS_PORT, System.getProperty("org.eclipse.equinox.http.jetty.https.port", "8443")))); //$NON-NLS-1$//$NON-NLS-2$
			properties.put(JettyConstants.SSL_KEYSTORE, preferences.get(SSL_KEYSTORE, "keystore")); //$NON-NLS-1$

			LogHelper.log(new Status(IStatus.INFO, PI_CONFIGURATOR, "Keystore absolute path is " + preferences.get(SSL_KEYSTORE, "keystore"))); //$NON-NLS-1$ //$NON-NLS-2$

			properties.put(JettyConstants.SSL_PASSWORD, preferences.get(SSL_PASSWORD, "password")); //$NON-NLS-1$
			properties.put(JettyConstants.SSL_KEYPASSWORD, preferences.get(SSL_KEYPASSWORD, "password")); //$NON-NLS-1$
			properties.put(JettyConstants.SSL_PROTOCOL, preferences.get(SSL_PROTOCOL, "SSLv3")); //$NON-NLS-1$
		}

		if (!httpsEnabled) {
			properties.put(JettyConstants.HTTP_ENABLED, true);
			properties.put(JettyConstants.HTTP_PORT, new Integer(preferences.get(HTTP_PORT, System.getProperty("org.eclipse.equinox.http.jetty.http.port", "8080")))); //$NON-NLS-1$ //$NON-NLS-2$
		}

		//properties for help system
		properties.put("other.info", "org.eclipse.help"); //$NON-NLS-1$ //$NON-NLS-2$ 

		JettyConfigurator.startServer("MasterJetty", properties); //$NON-NLS-1$
	}

	public void stop(BundleContext context) throws Exception {

		JettyConfigurator.stopServer("MasterJetty"); //$NON-NLS-1$

		if (authServiceTracker != null) {
			authServiceTracker.close();
			authServiceTracker = null;
		}

		if (packageAdminTracker != null) {
			packageAdminTracker.close();
			packageAdminTracker = null;
		}
	}

	protected Bundle getBundle(String symbolicName) {
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

	String getAuthName() {
		//lookup order is:
		// 1: Defined preference called "orion.auth.name"
		// 2: System property called "orion.tests.authtype"
		// 3: Default to Form+OpenID
		return PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_NAME, System.getProperty("orion.tests.authtype", DEFAULT_AUTHENTICATION_NAME)); //$NON-NLS-1$
	}
}