package org.eclipse.orion.internal.server.servlets.hosting;

import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;

/**
 * Service for managing hosted sites.
 * 
 * TODO: This should be replaced with a real protocol for managing hosted sites, in order to support 
 * remote hosting.
 */
public interface ISiteHostingService {

	public void start(SiteConfiguration siteConfig, WebUser user) throws SiteHostingException;

	public void stop(SiteConfiguration siteConfig, WebUser user) throws SiteHostingException;

	public IHostedSite get(SiteConfiguration siteConfig/*, WebUser user*/);

	public boolean isHosted(String host);

}
