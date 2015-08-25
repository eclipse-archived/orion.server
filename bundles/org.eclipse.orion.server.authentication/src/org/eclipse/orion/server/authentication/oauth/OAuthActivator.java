/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.oauth;

/**
 * An abstract class registering services of an authorization plug-in.
 * A new plug-in's Activator class should override getOAuthParamsFactory()
 * method, returning the specific OAuthParamsFactory object which is
 * being used for creating OAuthParams.
 *
 * @author mwlodarczyk
 *
 */
import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public abstract class OAuthActivator implements BundleActivator {
	ServiceRegistration oauthServiceRegistration;

	public void start(BundleContext context) throws Exception {
		OAuthParamsFactory oauthParamsFactory = getOAuthParamsFactory();

		Dictionary<String, String> properties = new Hashtable<String, String>();
		properties.put(OAuthParamsFactory.PROVIDER, oauthParamsFactory.getOAuthProviderName());

		oauthServiceRegistration = context.registerService(OAuthParamsFactory.class.getName(),
				oauthParamsFactory, properties);
	}

	public void stop(BundleContext context) throws Exception {
		oauthServiceRegistration.unregister();
	}

	protected abstract OAuthParamsFactory getOAuthParamsFactory();
}
