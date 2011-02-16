package org.eclipse.orion.internal.server.hosting;

import java.net.InetAddress;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;
import org.eclipse.orion.internal.server.servlets.site.SiteConfigurationConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Adds information about the hosting state of a site configuration to its JSON representation.
 */
public class HostedStatusDecorator implements IWebResourceDecorator {

	private static final String SITE_CONFIGURATION_SERVLET_ALIAS = "site"; //$NON-NLS-1$

	@Override
	public void addAtributesFor(URI resource, JSONObject representation) {
		IPath resourcePath = new Path(resource.getPath());
		if (resourcePath.segmentCount() == 0)
			return;
		String service = resourcePath.segment(0);
		if (!(SITE_CONFIGURATION_SERVLET_ALIAS.equals(service)))
			return;
		
		try {
			if (resourcePath.segmentCount() == 1) {
				// Decorating a request for all site configuration
				JSONArray siteConfigurations = representation.optJSONArray(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS);
				if (siteConfigurations != null) {
					for (int i=0; i < siteConfigurations.length(); i++) {
						addStatus(siteConfigurations.getJSONObject(i));
					}
				}
			} else if (resourcePath.segmentCount() == 2) {
				// Decorating a request for individual site configuration
				addStatus(representation);
			}
		} catch (JSONException e) {
			// Shouldn't happen
			// Since we are just decorating someone else's response we shouldn't cause a failure
			LogHelper.log(e);
		}
	}
	
	/**
	 * Adds status field to a representation of a site configuration.
	 */
	private void addStatus(JSONObject siteConfigJson) throws JSONException {
		String id = siteConfigJson.getString(ProtocolConstants.KEY_ID);
		SiteConfiguration siteConfiguration = SiteConfiguration.fromId(id);
		SiteHostingService hostingService = HostingActivator.getDefault().getHostingService();
		HostedSite site = hostingService.get(siteConfiguration);
		JSONObject hostingStatus = new JSONObject();
		if (site != null) {
			hostingStatus.put("Status", "started");
			// FIXME: scheme, port#? 
			hostingStatus.put("URL", site.getHost());
		} else {
			hostingStatus.put("Status", "stopped");
		}
		siteConfigJson.put(SiteConfigurationConstants.KEY_HOSTING_STATUS, hostingStatus);
	}
	
}
