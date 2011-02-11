package org.eclipse.orion.internal.server.hosting;

import java.net.URI;

import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.json.JSONObject;

public class SiteConfigurationHostingStatusDecorator implements IWebResourceDecorator{

	@Override
	public void addAtributesFor(URI resource, JSONObject representation) {
		// if it's a site configuration or a list of them
		// get the table, check if the site config is in it
		// if it is:    put HostedState: running
		// if it's not: put HostedState: stopped
	}
	
}
