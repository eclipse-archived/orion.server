package org.eclipse.orion.internal.server.hosting;

import java.net.InetAddress;
import java.net.URI;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.hosting.IHostedSite;
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
				// GET /site/ (get all site configs) or POST /site/ (create a site config) 
				JSONArray siteConfigurations = representation.optJSONArray(SiteConfigurationConstants.KEY_SITE_CONFIGURATIONS);
				if (siteConfigurations != null) {
					// It's the "get all" case
					for (int i=0; i < siteConfigurations.length(); i++) {
						addStatus(siteConfigurations.getJSONObject(i), resource);
					}
				} else {
					addStatus(representation, resource);
				}
			} else if (resourcePath.segmentCount() == 2) {
				// Decorating a request for individual site configuration
				addStatus(representation, resource);
			}
		} catch (JSONException e) {
			// Shouldn't happen, but since we are just decorating someone else's response we shouldn't cause a failure
			LogHelper.log(e);
		}
	}
	
	/**
	 * Adds status field to a representation of a site configuration.
	 * @param siteConfigJson The JSONObject representing a single site configuration.
	 * @param resource The original request passed to the decorator.
	 */
	private void addStatus(JSONObject siteConfigJson, URI resource) throws JSONException {
		String id = siteConfigJson.getString(ProtocolConstants.KEY_ID);
		SiteConfiguration siteConfiguration = SiteConfiguration.fromId(id);
		SiteHostingService hostingService = HostingActivator.getDefault().getHostingService();
		IHostedSite site = (IHostedSite) hostingService.get(siteConfiguration);
		JSONObject hostingStatus = new JSONObject();
		if (site != null) {
			hostingStatus.put("Status", "started");  //$NON-NLS-1$//$NON-NLS-2$
			// Whatever scheme was used to access the resource, assume it's used for the sites too
			String hostedUrl = resource.getScheme() + "://" + site.getHost(); //$NON-NLS-1$
			hostingStatus.put("URL", hostedUrl); //$NON-NLS-1$
			
		} else {
			hostingStatus.put("Status", "stopped"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		siteConfigJson.put(SiteConfigurationConstants.KEY_HOSTING_STATUS, hostingStatus);
	}
	
}
