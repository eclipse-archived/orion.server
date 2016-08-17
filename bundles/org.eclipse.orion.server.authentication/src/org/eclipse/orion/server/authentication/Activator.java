/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.users.UserConstants;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {
	/**
	 * The symbolic id of this bundle.
	 */
	public static final String PI_AUTHENTICATION_SERVLETS = "org.eclipse.orion.server.authentication"; //$NON-NLS-1$

	private static final Logger logger = LoggerFactory.getLogger(LogHelper.LOGGER_ID);

	private static volatile BundleContext bundleContext;

	public static Activator singleton;

	public static Activator getDefault() {
		return singleton;
	}

	public static BundleContext getBundleContext() {
		return bundleContext;
	}

	public void start(BundleContext context) throws Exception {
		singleton = this;
		bundleContext = context;

		AuthenticationMetaStoreJob job = new AuthenticationMetaStoreJob();
		job.schedule();
		logger.info("Started Orion server authentication successfully."); //$NON-NLS-1$
	}

	public void stop(BundleContext context) throws Exception {
		bundleContext = null;
	}

	class AuthenticationMetaStoreJob extends Job {

		/**
		 * @param name
		 */
		public AuthenticationMetaStoreJob() {
			super("AuthenticationMetaStoreJob");
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				IMetaStore metastore = OrionConfiguration.getMetaStore();
				if (metastore == null) {
					String msg = "Error starting Orion authentication service. Server metastore is unavailable! Checking again in 5 seconds";
					logger.error(msg);
					schedule(5000);
					return Status.OK_STATUS;
				}
				// register property keys for user caches for user lookups.
				List<String> keys = new ArrayList<String>();
				keys.add(UserConstants.EMAIL);
				keys.add(UserConstants.OAUTH);
				keys.add(UserConstants.OPENID);
				metastore.registerUserProperties(keys);
				return Status.OK_STATUS;
			} catch (CoreException e) {
				String msg = "Error starting Orion server authentication service.";
				logger.error(msg, e);
				return new Status(Status.ERROR, PI_AUTHENTICATION_SERVLETS, msg, e);
			}
		}
	}
}