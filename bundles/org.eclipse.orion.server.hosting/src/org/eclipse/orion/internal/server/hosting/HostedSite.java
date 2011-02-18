package org.eclipse.orion.internal.server.hosting;

import java.util.*;
import org.eclipse.orion.internal.server.servlets.hosting.IHostedSite;
import org.eclipse.orion.internal.server.servlets.site.SiteConfiguration;
import org.eclipse.orion.internal.server.servlets.site.SiteConfigurationConstants;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.json.*;

class HostedSite implements IHostedSite {

	private Map<String, String> mappings;
	private String userName;
	private String workspaceId;
	private String host;
	private String editServer;

	public HostedSite(SiteConfiguration siteConfig, WebUser user, String host, String editServer) {
		this.mappings = Collections.unmodifiableMap(createMap(siteConfig));
		this.userName = user.getName();
		this.workspaceId = siteConfig.getWorkspace();
		this.host = host;
		this.editServer = editServer;

		if (this.userName == null || this.workspaceId == null || this.host == null || this.editServer == null) {
			throw new IllegalArgumentException("Parameters must be nonnull");
		}
	}

	private static Map<String, String> createMap(SiteConfiguration siteConfig) {
		Map<String, String> map = new HashMap<String, String>();
		JSONArray mappingsJson = siteConfig.getMappingsJSON();
		for (int i = 0; i < mappingsJson.length(); i++) {
			try {
				JSONObject mapping = (JSONObject) mappingsJson.get(i);
				String source = mapping.optString(SiteConfigurationConstants.KEY_SOURCE, null);
				String target = mapping.optString(SiteConfigurationConstants.KEY_TARGET, null);
				if (source != null && target != null) {
					map.put(source, target);
				}
			} catch (JSONException e) {
				// Shouldn't happen
			}
		}
		return map;
	}

	@Override
	public Map<String, String> getMappings() {
		return mappings;
	}

	@Override
	public String getUserName() {
		return userName;
	}

	@Override
	public String getWorkspaceId() {
		return workspaceId;
	}

	@Override
	public String getHost() {
		return host;
	}

	@Override
	public String getEditServerUrl() {
		return editServer;
	}

}