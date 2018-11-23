/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
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
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = App.TYPE)
public class App extends CFObject {
	public static final String RESOURCE = "apps"; //$NON-NLS-1$
	public static final String TYPE = "App"; //$NON-NLS-1$

	private JSONObject summaryJSON;
	private JSONObject appJSON;

	private String name;
	private String guid;

	private ManifestParseTree manifest;

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(CFProtocolConstants.KEY_URL) //
		};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	public ManifestParseTree getManifest() {
		return manifest;
	}

	public void setManifest(ManifestParseTree manifest) {
		this.manifest = manifest;
	}

	public String getGuid() {
		return guid;
	}

	public void setGuid(String guid) {
		this.guid = guid;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		// TODO Auto-generated method stub
		return null;
	}

	public JSONObject getSummaryJSON() {
		return summaryJSON;
	}

	public void setSummaryJSON(JSONObject summaryJSON) {
		this.summaryJSON = summaryJSON;
	}

	public JSONObject getAppJSON() {
		return appJSON;
	}

	public void setAppJSON(JSONObject appJSON) {
		this.appJSON = appJSON;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		return summaryJSON;
		//		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}
}
