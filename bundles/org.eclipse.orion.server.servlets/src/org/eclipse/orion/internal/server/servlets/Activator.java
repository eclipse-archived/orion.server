/*******************************************************************************
 * Copyright (c) 2009, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.eclipse.orion.internal.server.servlets.workspace.ProjectParentDecorator;
import org.eclipse.orion.internal.server.servlets.workspace.authorization.AuthorizationService;
import org.eclipse.orion.internal.server.servlets.xfer.TransferResourceDecorator;
import org.eclipse.orion.server.authentication.IAuthenticationService;
import org.eclipse.orion.server.authentication.NoneAuthenticationService;
import org.eclipse.orion.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Activator for the server servlet bundle. Responsible for tracking required services
 * and registering/unregistering servlets.
 */
public class Activator implements BundleActivator {

	private static final String ADMIN_LOGIN_VALUE = "admin"; //$NON-NLS-1$
	private static final String ADMIN_NAME_VALUE = "Administrative User"; //$NON-NLS-1$

	public static volatile BundleContext bundleContext;

	/**
	 * Global flag for enabling debug tracing
	 */
	public static final boolean DEBUG = true;

	public static final String LOCATION_FILE_SERVLET = "/file"; //$NON-NLS-1$
	public static final String LOCATION_WORKSPACE_SERVLET = "/workspace"; //$NON-NLS-1$

	public static final String PI_SERVER_SERVLETS = "org.eclipse.orion.server.servlets"; //$NON-NLS-1$
	public static final String PROP_USER_AREA = "org.eclipse.orion.server.core.userArea"; //$NON-NLS-1$

	public static final String DEFAULT_AUTHENTICATION_NAME = "FORM+OAuth"; //$NON-NLS-1$
	/**
	 * Service reference property indicating if the authentication service has been configured.
	 */
	static final String PROP_CONFIGURED = "configured"; //$NON-NLS-1$

	static Activator singleton;

	private ServiceTracker<IWebResourceDecorator, IWebResourceDecorator> decoratorTracker;

	private ServiceRegistration<IWebResourceDecorator> transferDecoratorRegistration;
	private ServiceRegistration<IWebResourceDecorator> parentDecoratorRegistration;

	private AuthServiceTracker authServiceTracker;

	public static Activator getDefault() {
		return singleton;
	}

	public BundleContext getContext() {
		return bundleContext;
	}

	private synchronized ServiceTracker<IWebResourceDecorator, IWebResourceDecorator> getDecoratorTracker() {
		if (decoratorTracker == null) {
			decoratorTracker = new ServiceTracker<IWebResourceDecorator, IWebResourceDecorator>(bundleContext, IWebResourceDecorator.class, null);
			decoratorTracker.open();
		}
		return decoratorTracker;
	}

	public Collection<IWebResourceDecorator> getWebResourceDecorators() {
		ServiceTracker<IWebResourceDecorator, IWebResourceDecorator> tracker = getDecoratorTracker();
		return tracker.getTracked().values();
	}

	/**
	 * Registers services supplied by this bundle
	 */
	private void registerServices() {
		//adds the import/export locations to representations
		transferDecoratorRegistration = bundleContext.registerService(IWebResourceDecorator.class, new TransferResourceDecorator(), null);
		//adds parent links to representations
		parentDecoratorRegistration = bundleContext.registerService(IWebResourceDecorator.class, new ProjectParentDecorator(), null);
	}

	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;
		registerServices();
		authServiceTracker = new AuthServiceTracker(context);
		authServiceTracker.open();
		initializeAdminUser();
	}

	public void stop(BundleContext context) throws Exception {
		if (authServiceTracker != null) {
			authServiceTracker.close();
			authServiceTracker = null;
		}

		if (decoratorTracker != null) {
			decoratorTracker.close();
			decoratorTracker = null;
		}
		unregisterServices();
		bundleContext = null;
	}

	private void unregisterServices() {
		if (transferDecoratorRegistration != null) {
			transferDecoratorRegistration.unregister();
			transferDecoratorRegistration = null;
		}
		if (parentDecoratorRegistration != null) {
			parentDecoratorRegistration.unregister();
			parentDecoratorRegistration = null;
		}
	}

	public IAuthenticationService getAuthService() {
		return authServiceTracker.getService();
	}

	String getAuthName() {
		//lookup order is:
		// 1: Defined preference called "orion.auth.name"
		// 2: System property called "orion.tests.authtype"
		// 3: Default to Form+OAuth
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

	/**
	 * Initialize the admin user on the server.
	 */
	private void initializeAdminUser() {
		try {
			// initialize the admin account in the IMetaStore
			String adminDefaultPassword = PreferenceHelper.getString(ServerConstants.CONFIG_AUTH_ADMIN_DEFAULT_PASSWORD);
			Boolean adminUserFolderExists = SimpleMetaStoreUtil.readMetaUserFolder(OrionConfiguration.getRootLocation().toLocalFile(EFS.NONE, null), ADMIN_LOGIN_VALUE).exists();
			if (!adminUserFolderExists && adminDefaultPassword != null) {
				UserInfo userInfo = new UserInfo();
				userInfo.setUserName(ADMIN_LOGIN_VALUE);
				userInfo.setFullName(ADMIN_NAME_VALUE);
				userInfo.setProperty(UserConstants.PASSWORD, adminDefaultPassword);
				OrionConfiguration.getMetaStore().createUser(userInfo);

				try {
					AuthorizationService.addUserRight(ADMIN_LOGIN_VALUE, "/users");
					AuthorizationService.addUserRight(ADMIN_LOGIN_VALUE, "/users/*"); //$NON-NLS-1$
				} catch (CoreException e) {
					LogHelper.log(e);
				}

				Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.account"); //$NON-NLS-1$
				if (logger.isInfoEnabled()) {
					logger.info("Account created: " + ADMIN_LOGIN_VALUE); //$NON-NLS-1$
				}
			}
		} catch (CoreException e) {
			LogHelper.log(e);
		}
	}

}
