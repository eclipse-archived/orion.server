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
package org.eclipse.e4.webide.server.useradmin;

import javax.servlet.ServletException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.e4.webide.internal.server.useradmin.servlets.AdminHttpContext;
import org.eclipse.e4.webide.internal.server.useradmin.servlets.UsersAdminServlet;
import org.eclipse.e4.webide.server.LogHelper;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.useradmin.UserAdmin;
import org.osgi.util.tracker.ServiceTracker;

public class UserAdminActivator implements BundleActivator {

	/**
	 * The symbolic id of this bundle.
	 */
	public static final String PI_USERADMIN = "org.eclipse.e4.webide.server.useradmin"; //$NON-NLS-1$

	private static UserAdminActivator singleton;
	private BundleContext bundleContext;

	public BundleContext getBundleContext() {
		return bundleContext;
	}

	public static UserAdminActivator getDefault() {
		return singleton;
	}

	private HttpServiceTracker httpServiceTracker;
	private ServiceTracker<UserAdmin, UserAdmin> userAdminServiceTracker;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext bundleContext) throws Exception {
		singleton = this;
		this.bundleContext = bundleContext;

		httpServiceTracker = new HttpServiceTracker(bundleContext);
		httpServiceTracker.open();

		userAdminServiceTracker = new ServiceTracker<UserAdmin, UserAdmin>(
				bundleContext, UserAdmin.class, null);
		userAdminServiceTracker.open();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		if (httpServiceTracker != null) {
			httpServiceTracker.close();
			httpServiceTracker = null;
		}
		if (userAdminServiceTracker != null) {
			userAdminServiceTracker.close();
			userAdminServiceTracker = null;
		}
		this.bundleContext = null;
	}

	public UserAdmin getUserAdminService() {
		return userAdminServiceTracker.getService();
	}

	private class HttpServiceTracker extends
			ServiceTracker<HttpService, HttpService> {

		public HttpServiceTracker(BundleContext context) {
			super(context, HttpService.class.getName(), null);
		}

		public HttpService addingService(ServiceReference<HttpService> reference) {
			HttpService httpService = super.addingService(reference);
			if (httpService == null)
				return null;

			try {
				AdminHttpContext adminHttpContext = new AdminHttpContext(
						context.getBundle());
				httpService.registerServlet("/users", new UsersAdminServlet(), //$NON-NLS-1$
						null, adminHttpContext);
				httpService.registerResources("/usersstatic", "/static", //$NON-NLS-1$ //$NON-NLS-2$
						adminHttpContext);
			} catch (ServletException e) {
				LogHelper.log(new Status(IStatus.ERROR, PI_USERADMIN, 1,
						"An error occured when registering servlets", e));
			} catch (NamespaceException e) {
				LogHelper.log(new Status(IStatus.ERROR, PI_USERADMIN, 1,
						"A namespace error occured when registering servlets",
						e));
			}
			return httpService;
		}

		public void removedService(ServiceReference<HttpService> reference,
				HttpService httpService) {
			httpService.unregister("/users"); //$NON-NLS-1$
			httpService.unregister("/usersstatic"); //$NON-NLS-1$
			// calls context.ungetService(reference);
			super.removedService(reference, httpService); 
		}
	}

}
