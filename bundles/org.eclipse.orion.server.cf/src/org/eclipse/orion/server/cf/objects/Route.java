/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.objects;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.*;

@ResourceDescription(type = Route.TYPE)
public class Route extends CFObject {

	public static final String RESOURCE = "routes"; //$NON-NLS-1$
	public static final String TYPE = "Route"; //$NON-NLS-1$

	protected static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(CFProtocolConstants.KEY_GUID), //
				new Property(CFProtocolConstants.KEY_HOST), //
				new Property(CFProtocolConstants.KEY_DOMAIN_NAME), //
				new Property(CFProtocolConstants.KEY_APPS) //
		};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	private JSONObject routeJSON;
	private List<App2> apps;

	public Route setCFJSON(JSONObject routeJSON) {
		this.routeJSON = routeJSON;
		return this;
	}

	public JSONObject getCFJSON() {
		return this.routeJSON;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return null;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_HOST)
	public String getHost() {
		try {
			return routeJSON.getJSONObject("entity").getString("host");
		} catch (JSONException e) {
			return null;
		}
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_DOMAIN_NAME)
	public String getDomainName() {
		try {
			return routeJSON.getJSONObject("entity").getJSONObject("domain").getJSONObject("entity").getString("name");
		} catch (JSONException e) {
			return null;
		}
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_GUID)
	public String getGuid() {
		try {
			return routeJSON.getJSONObject("metadata").getString("guid");
		} catch (JSONException e) {
			return null;
		}
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_APPS)
	private JSONArray getAppsJSON() {
		try {
			JSONArray ret = new JSONArray();
			if (apps == null) {
				apps = new ArrayList<App2>();
				JSONArray appsJSON = routeJSON.getJSONObject("entity").getJSONArray("apps");

				for (int i = 0; i < appsJSON.length(); i++) {
					App2 app = new App2().setCFJSON(appsJSON.getJSONObject(i));
					apps.add(app);
					ret.put(app.toJSON());
				}
			}
			return ret;
		} catch (JSONException e) {
			return null;
		}
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}
}
