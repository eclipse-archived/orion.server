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
package org.eclipse.e4.webide.server.configurator;

import static org.eclipse.e4.internal.webide.server.servlets.Activator.LOCATION_FILE_SERVLET;
import static org.eclipse.e4.internal.webide.server.servlets.Activator.LOCATION_PROJECT_SERVLET;

import java.io.File;
import java.io.FileInputStream;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.ServletException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.e4.internal.webide.server.servlets.file.FileSystemsServlet;
import org.eclipse.e4.internal.webide.server.servlets.file.NewFileServlet;
import org.eclipse.e4.internal.webide.server.servlets.project.ProjectServlet;
import org.eclipse.e4.internal.webide.server.servlets.workspace.WorkspaceServlet;
import org.eclipse.e4.webide.server.LogHelper;
import org.eclipse.e4.webide.server.configurator.authentication.IAuthenticationService;
import org.eclipse.e4.webide.server.configurator.authentication.NoneAuthenticationService;
import org.eclipse.e4.webide.server.configurator.configuration.ConfigurationFormat;
import org.eclipse.e4.webide.server.configurator.configuration.PropertyReader;
import org.eclipse.e4.webide.server.configurator.httpcontext.BundleEntryHttpContext;
import org.eclipse.e4.webide.server.servlets.PreferencesServlet;
import org.eclipse.equinox.http.jetty.JettyConfigurator;
import org.eclipse.equinox.http.jetty.JettyConstants;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;

public class ConfiguratorActivator implements BundleActivator {

	/**
	 * The symbolic id of this bundle.
	 */
	public static final String PI_CONFIGURATOR = "org.eclipse.e4.webide.server.configurator"; //$NON-NLS-1$
	private PropertyReader propertyReader;

	static ConfiguratorActivator singleton;

	private ServiceTracker<HttpService, HttpService> httpServiceTracker;
	private ServiceTracker<IAuthenticationService, IAuthenticationService> authServiceTracker;

	private class HttpServiceTracker extends ServiceTracker<HttpService, HttpService> {

		public HttpServiceTracker(BundleContext context) {
			super(context, HttpService.class.getName(), null);
		}

		public HttpService addingService(ServiceReference<HttpService> reference) {
			HttpService httpService = super.addingService(reference);
			if (httpService == null)
				return null;

			HttpContext httpContext = new BundleEntryHttpContext(context.getBundle(), propertyReader.getProperties());

			try {
				NewFileServlet fileServlet = new NewFileServlet(org.eclipse.e4.internal.webide.server.servlets.Activator.getDefault().getRootLocationURI());
				httpService.registerServlet(LOCATION_FILE_SERVLET, fileServlet, null, httpContext);
				httpService.registerServlet("/filesystems", new FileSystemsServlet(), null, httpContext); //$NON-NLS-1$
				httpService.registerServlet("/prefs", new PreferencesServlet(), null, httpContext);//$NON-NLS-1$
				httpService.registerServlet(LOCATION_PROJECT_SERVLET, new ProjectServlet(), null, httpContext);
				httpService.registerServlet("/workspace", new WorkspaceServlet(fileServlet), null, httpContext); //$NON-NLS-1$
			} catch (ServletException e) {
				LogHelper.log(new Status(IStatus.ERROR, PI_CONFIGURATOR, 1, "An error occured when registering servlets", e));
			} catch (NamespaceException e) {
				LogHelper.log(new Status(IStatus.ERROR, PI_CONFIGURATOR, 1,	"A namespace error occured when registering servlets", e));
			}
			return httpService;
		}

		public void removedService(ServiceReference<HttpService> reference, HttpService httpService) {
			httpService.unregister(LOCATION_FILE_SERVLET);
			httpService.unregister("/filesystems");//$NON-NLS-1$
			httpService.unregister("/prefs");//$NON-NLS-1$
			httpService.unregister(LOCATION_PROJECT_SERVLET);
			httpService.unregister("/workspace"); //$NON-NLS-1$
			super.removedService(reference, httpService);
		}
	}

	private Filter getAuthFilter() throws InvalidSyntaxException {
		StringBuilder sb = new StringBuilder("(");
		sb.append(ConfigurationFormat.AUTH_NAME);
		sb.append("=");
		sb.append(propertyReader.getAuthName());
		sb.append(")");
		return FrameworkUtil.createFilter(sb.toString());
	}

	private class AuthServiceTracker extends ServiceTracker<IAuthenticationService, IAuthenticationService> {

		public AuthServiceTracker(BundleContext context) throws InvalidSyntaxException {
			super(context, getAuthFilter(), null);
			// TODO: Filters are case sensitive, we should be too
			if (NoneAuthenticationService.AUTH_TYPE.equalsIgnoreCase(propertyReader.getAuthName())) {
				Dictionary<String, String> properties = new Hashtable<String, String>();
				properties.put(ConfigurationFormat.AUTH_NAME, propertyReader.getAuthName());
				// TODO: shouldn't we always register the none-auth service?
				context.registerService(IAuthenticationService.class, new NoneAuthenticationService(), properties);
			}
		}

		@Override
		public IAuthenticationService addingService(ServiceReference<IAuthenticationService> reference) {
			IAuthenticationService authService = super.addingService(reference);
			authService.configure(propertyReader.getProperties());
			return authService;
		}
	}

	public static ConfiguratorActivator getDefault() {
		return singleton;
	}

	public void start(BundleContext context) throws Exception {
		singleton = this;

		String configurationFile = Platform.getConfigurationLocation().getURL().getPath() + "/configuration.xml"; //$NON-NLS-1$

		if (!(new File(configurationFile).exists())) {
			configurationFile = "configuration.xml"; //$NON-NLS-1$
			propertyReader = new PropertyReader(context.getBundle().getEntry(configurationFile).openStream());
		} else {
			propertyReader = new PropertyReader(new FileInputStream(new File(configurationFile)));
		}

		Boolean httpsEnabled = propertyReader.getHttpsEnabled();

		Dictionary<String, Object> properties = new Hashtable<String, Object>();

		if (httpsEnabled) {
			LogHelper.log(new Status(IStatus.INFO, PI_CONFIGURATOR,	"Https is enabled", null));
			// properties.put(JettyConstants.HTTP_PORT,8080);
			properties.put(JettyConstants.HTTPS_ENABLED, propertyReader.getHttpsEnabled());
			properties.put(JettyConstants.HTTPS_PORT, propertyReader.getHttpsPort());
			properties.put(JettyConstants.SSL_KEYSTORE, propertyReader.getSslKeystore());
			LogHelper.log(new Status(IStatus.INFO, PI_CONFIGURATOR,	"Jetty Constant for SSL Keystore is " + properties.get(JettyConstants.SSL_KEYSTORE), null));
			properties.put(JettyConstants.SSL_PASSWORD, propertyReader.getSslPassword());
			properties.put(JettyConstants.SSL_KEYPASSWORD, propertyReader.getSslKeyPassword());
			properties.put(JettyConstants.SSL_PROTOCOL, propertyReader.getSslProtocol());
		}

		if (!httpsEnabled) {
			// TODO HTTP_PORT should also be configurable by the user
			properties.put(JettyConstants.HTTP_ENABLED, true);
			properties.put(JettyConstants.HTTP_PORT, 8080);
		}

		JettyConfigurator.startServer("MasterJetty", properties);

		httpServiceTracker = new HttpServiceTracker(context);
		httpServiceTracker.open();

		authServiceTracker = new AuthServiceTracker(context);
		authServiceTracker.open();
	}

	public void stop(BundleContext context) throws Exception {
		if (httpServiceTracker != null) {
			httpServiceTracker.close();
			httpServiceTracker = null;
		}
		if (authServiceTracker != null) {
			authServiceTracker.close();
			authServiceTracker = null;
		}
	}

	public IAuthenticationService getAuthService() {
		return authServiceTracker.getService();
	}
}
