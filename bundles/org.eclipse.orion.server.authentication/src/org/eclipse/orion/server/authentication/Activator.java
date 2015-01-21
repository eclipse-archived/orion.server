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
		IMetaStore metastore =  OrionConfiguration.getMetaStore();
		List<String> keys = new ArrayList<String>();
		keys.add(UserConstants.EMAIL);
		keys.add(UserConstants.OAUTH);
		keys.add(UserConstants.OPENID);
		metastore.registerUserProperties(keys);
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
		if (logger.isDebugEnabled()) {
			logger.debug("Started orion server authentication."); //$NON-NLS-1$
		}

	}

	public void stop(BundleContext context) throws Exception {
		bundleContext = null;
	}
}
