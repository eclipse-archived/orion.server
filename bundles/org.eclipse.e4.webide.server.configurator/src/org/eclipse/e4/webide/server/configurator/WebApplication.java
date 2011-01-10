/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.webide.server.configurator;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class WebApplication implements IApplication {
	private static final String EQUINOX_HTTP_JETTY = "org.eclipse.equinox.http.jetty"; //$NON-NLS-1$
	private static final String EQUINOX_HTTP_REGISTRY = "org.eclipse.equinox.http.registry"; //$NON-NLS-1$
	private IApplicationContext appContext;

	public Object start(IApplicationContext context) throws Exception {
		appContext = context;
		ensureBundleStarted(EQUINOX_HTTP_JETTY);
		ensureBundleStarted(EQUINOX_HTTP_REGISTRY);
		return IApplicationContext.EXIT_ASYNC_RESULT;
	}

	public void stop() {
		if (appContext != null)
			appContext.setResult(EXIT_OK, this);
	}

	private void ensureBundleStarted(String symbolicName) throws BundleException {
		Bundle bundle = ConfiguratorActivator.getDefault().getBundle(symbolicName);
		if (bundle != null) {
			if (bundle.getState() == Bundle.RESOLVED || bundle.getState() == Bundle.STARTING) {
				bundle.start();
			}
		}
	}

}
