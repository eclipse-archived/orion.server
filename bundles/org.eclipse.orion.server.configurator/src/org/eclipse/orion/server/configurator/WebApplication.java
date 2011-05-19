/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.configurator;

import java.io.File;
import java.io.IOException;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main application for the Orion server. This application just starts the required
 * server bundles and allows the application to complete asynchronously when
 * closed from the OSGi console.
 */
public class WebApplication implements IApplication {
	/**
	 * A special return code that will be recognized by the PDE launcher and used to
	 * show an error dialog if the workspace is locked.
	 */
	private static final Integer EXIT_WORKSPACE_LOCKED = new Integer(15);

	private static final String EQUINOX_HTTP_JETTY = "org.eclipse.equinox.http.jetty"; //$NON-NLS-1$
	private static final String EQUINOX_HTTP_REGISTRY = "org.eclipse.equinox.http.registry"; //$NON-NLS-1$
	private IApplicationContext appContext;

	public Object start(IApplicationContext context) throws Exception {
		appContext = context;
		ensureBundleStarted(EQUINOX_HTTP_JETTY);
		ensureBundleStarted(EQUINOX_HTTP_REGISTRY);
		context.applicationRunning();
		Object instanceLocationCheck = checkInstanceLocation();
		if (instanceLocationCheck != null) {
			return instanceLocationCheck;
		}
		return IApplicationContext.EXIT_ASYNC_RESULT;
	}

	public void stop() {
		if (appContext != null)
			appContext.setResult(EXIT_OK, this);
	}

	private Object checkInstanceLocation() {
		Location instanceLoc = ConfiguratorActivator.getDefault().getInstanceLocation();
		// -data must be specified
		if (instanceLoc == null || !instanceLoc.isSet()) {
			getLogger().error("Instance location must be set"); //$NON-NLS-1$
			return EXIT_OK;
		}

		// at this point its valid, so try to lock it
		try {
			if (instanceLoc.lock()) {
				getLogger().info("Workspace location locked successfully: " + instanceLoc.getURL()); //$NON-NLS-1$
				return null;
			}
		} catch (IOException e) {
			getLogger().error("Workspace location could not be locked: " + instanceLoc.getURL()); //$NON-NLS-1$
		}

		// we failed to create the directory.  
		// Two possibilities:
		// 1. directory is already in use
		// 2. directory could not be created
		File workspaceDirectory = new File(instanceLoc.getURL().getFile());
		if (workspaceDirectory.exists()) {
			getLogger().error("The workspace location is already in use by another server instance: " + workspaceDirectory); //$NON-NLS-1$
			return EXIT_WORKSPACE_LOCKED;
		}
		getLogger().error("Workspace location could not be created: " + workspaceDirectory); //$NON-NLS-1$
		return EXIT_OK;
	}

	private Logger getLogger() {
		return LoggerFactory.getLogger("org.eclipse.orion.app");
	}

	private void ensureBundleStarted(String symbolicName) throws BundleException {
		Bundle bundle = ConfiguratorActivator.getDefault().getBundle(symbolicName);
		if (bundle != null) {
			if (bundle.getState() == Bundle.RESOLVED || bundle.getState() == Bundle.STARTING) {
				bundle.start(Bundle.START_TRANSIENT);
			}
		}
	}

}
