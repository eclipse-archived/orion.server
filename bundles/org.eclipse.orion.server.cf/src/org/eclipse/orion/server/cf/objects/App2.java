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
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = App2.TYPE)
public class App2 extends CFObject {

	public static final String RESOURCE = "apps"; //$NON-NLS-1$
	public static final String TYPE = "App"; //$NON-NLS-1$

	protected static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(CFProtocolConstants.KEY_GUID), //
				new Property(CFProtocolConstants.KEY_NAME) //
		};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	private JSONObject appJSON;

	public App2 setCFJSON(JSONObject appJSON) {
		this.appJSON = appJSON;
		return this;
	}

	public JSONObject getCFJSON() {
		return this.appJSON;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return null;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_NAME)
	public String getName() {
		try {
			return appJSON.getJSONObject("entity").getString("name");
		} catch (JSONException e) {
			return null;
		}
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_GUID)
	public String getGuid() {
		try {
			return appJSON.getJSONObject("metadata").getString("guid");
		} catch (JSONException e) {
			return null;
		}
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}
}
