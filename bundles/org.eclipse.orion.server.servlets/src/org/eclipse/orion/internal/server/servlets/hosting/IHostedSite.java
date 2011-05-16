package org.eclipse.orion.internal.server.servlets.hosting;

import java.util.List;
import java.util.Map;

public interface IHostedSite {

	/**
	 * @return The id of the SiteConfiguration this hosted site was launched from.
	 */
	public String getSiteConfigurationId();

	/**
	 * @return Mappings defined by the site configuration that this hosted site was launched from.
	 */
	public Map<String, List<String>> getMappings();

	/**
	 * @return The name of the user who launched this site.
	 */
	public String getUserName();

	/**
	 * @return Workspace id that this site will use.
	 */
	public String getWorkspaceId();

	/**
	 * @return The host where this site is accessible (hostname:port).
	 */
	public String getHost();

	/**
	 * @return The URL of the Orion server where the hosted files can be edited.
	 */
	public String getEditServerUrl();

}
