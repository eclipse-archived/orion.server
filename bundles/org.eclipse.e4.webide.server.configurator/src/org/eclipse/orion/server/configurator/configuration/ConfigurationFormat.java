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
package org.eclipse.orion.server.configurator.configuration;

import org.eclipse.equinox.http.jetty.JettyConstants;

public class ConfigurationFormat {
	public static final String AUTHENTICATION_NAME = "Auth-name";
	public static final String DEFAULT_AUTHENTICATION_NAME = "FORM+OpenID";

	private static final String JETTY = "jetty";

	public static final String HTTPS_ENABLED = JETTY + "." + JettyConstants.HTTPS_ENABLED;
	public static final String HTTPS_PORT = JETTY + "." + JettyConstants.HTTPS_PORT;
	public static final String HTTP_PORT = JETTY + "." + JettyConstants.HTTP_PORT;
	public static final String SSL_KEYSTORE = JETTY + "." + JettyConstants.SSL_KEYSTORE;
	public static final String SSL_PASSWORD = JETTY + "." + JettyConstants.SSL_PASSWORD;
	public static final String SSL_KEYPASSWORD = JETTY + "." + JettyConstants.SSL_KEYPASSWORD;
	public static final String SSL_PROTOCOL = JETTY + "." + JettyConstants.SSL_PROTOCOL;
}
