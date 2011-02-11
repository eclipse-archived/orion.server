package org.eclipse.orion.internal.server.servlets.hosting;

import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;

/**
 * Service for managing hosted sites.
 * 
 * TODO: This should be replaced with a protocol for managing hosted sites, in order to support 
 * remote hosting. The current API requires the site-configuration server and the hosting server
 * to be the same.
 */
public interface ISiteHostingService {

	public void start(SiteConfiguration siteConfig);

	public void stop(SiteConfiguration siteConfig);

	public boolean isRunning(SiteConfiguration siteConfig);

}
