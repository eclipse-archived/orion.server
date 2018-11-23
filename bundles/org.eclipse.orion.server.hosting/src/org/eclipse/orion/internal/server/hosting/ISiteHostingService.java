/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.hosting;

import java.net.URI;
import org.eclipse.orion.server.core.metastore.UserInfo;

/**
 * API for a service that can launch hosted sites from a site configuration, and query hosted 
 * sites that were launched.<p>
 * 
 * This API allows at most one hosted site to be running concurrently for any given 
 * <i>WebUser + SiteConfiguration</i> pair.<p>
 * 
 * TODO: This should be replaced with a real protocol for managing hosted sites and support
 * remote hosting.
 */
public interface ISiteHostingService {

	/**
	 * Starts a site configuration for the given user.
	 * @param siteConfig
	 * @param user
	 * @param editServer
	 * @param requestURI
	 * @throws SiteHostingException If starting failed.
	 */
	public void start(SiteInfo siteConfig, UserInfo user, String editServer, URI requestURI) throws SiteHostingException;

	/**
	 * Stops the user's hosted site which was launched from the site configuration.
	 * @param siteConfig
	 * @param user
	 * @throws SiteHostingException If stopping failed.
	 */
	public void stop(SiteInfo siteConfig, UserInfo user) throws SiteHostingException;

	/**
	 * Returns the hosted site matching the given configuration.
	 * @param user 
	 * @return The hosted site launched by <code>user</code> from <code>siteConfig</code>, or
	 * <code>null</code> if there is no such hosted site.
	 */
	public IHostedSite get(SiteInfo siteConfig, UserInfo user);

	/**
	 * @param host A host name.
	 * @return <code>true</code> if there is a hosted site running at <code>host</code>.
	 */
	public boolean isHosted(String host);

	/**
	 * @param host A host name.
	 * @return <code>true</code> if <code>host</code> is a virtual host name that a site could
	 * be running on.
	 */
	public boolean matchesVirtualHost(String host);

}
