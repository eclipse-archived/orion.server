/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 * Red Hat - Fix for bug 368061
 *******************************************************************************/
package org.eclipse.orion.server.configurator;

import java.util.*;
import org.eclipse.orion.server.authentication.IAuthenticationService;
import org.eclipse.orion.server.authentication.NoneAuthenticationService;
import org.eclipse.orion.server.core.*;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.*;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

@SuppressWarnings("deprecation")
public class ConfiguratorActivator implements BundleActivator {

	/**
	 * The symbolic id of this bundle.
	 */
	public static final String PI_CONFIGURATOR = "org.eclipse.orion.server.configurator"; //$NON-NLS-1$
	public static final String DEFAULT_AUTHENTICATION_NAME = "FORM+OpenID"; //$NON-NLS-1$
	/**
	 * Service reference property indicating if the authentication service has been configured.
	 */
	static final String PROP_CONFIGURED = "configured"; //$NON-NLS-1$
	static ConfiguratorActivator singleton;

	private ServiceTracker<IAuthenticationService, IAuthenticationService> authServiceTracker;
	private ServiceTracker<PackageAdmin, PackageAdmin> packageAdminTracker;
	private ServiceTracker<Location, Location> instanceLocationTracker;
	private BundleContext bundleContext;

	public static ConfiguratorActivator getDefault() {
		return singleton;
	}

	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;

		packageAdminTracker = new ServiceTracker<PackageAdmin, PackageAdmin>(context, PackageAdmin.class.getName(), null);
		packageAdminTracker.open();

		authServiceTracker = new AuthServiceTracker(context);
		authServiceTracker.open();
	}

	public void stop(BundleContext context) throws Exception {

		if (authServiceTracker != null) {
			authServiceTracker.close();
			authServiceTracker = null;
		}

		if (packageAdminTracker != null) {
			packageAdminTracker.close();
			packageAdminTracker = null;
		}

		if (instanceLocationTracker != null) {
			instanceLocationTracker.close();
			instanceLocationTracker = null;
		}
	}

	public IAuthenticationService getAuthService() {
		return authServiceTracker.getService();
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

	String getAuthName() {
		//lookup order is:
		// 1: Defined preference called "orion.auth.name"
		// 2: System property called "orion.tests.authtype"
		// 3: Default to Form+OpenID
		return PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_NAME, System.getProperty("orion.tests.authtype", DEFAULT_AUTHENTICATION_NAME)); //$NON-NLS-1$
	}

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
			Dictionary dictionary = new Properties();
			dictionary.put(PROP_CONFIGURED, "true"); //$NON-NLS-1$
			if (getService() != null) {
				getService().setRegistered(false);
			}
			authService.setRegistered(true);
			context.registerService(IAuthenticationService.class.getName(), authService, dictionary);
			return authService;
		}
	}
}