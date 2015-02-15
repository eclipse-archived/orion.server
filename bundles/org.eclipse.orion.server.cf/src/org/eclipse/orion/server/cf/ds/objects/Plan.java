/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.ds.objects;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.objects.CFObject;
import org.eclipse.orion.server.cf.objects.Manifest;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Plan.TYPE)
public class Plan extends CFObject {

	public static final String RESOURCE = "plans"; //$NON-NLS-1$
	public static final String TYPE = "Plan"; //$NON-NLS-1$

	protected static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(CFProtocolConstants.KEY_DEPLOYMENT_PLANNER), //
				new Property(CFProtocolConstants.KEY_APPLICATION_TYPE), //
				new Property(CFProtocolConstants.KEY_DEPLOYMENT_WIZARD), //
				new Property(CFProtocolConstants.KEY_REQUIRED), //
				new Property(Manifest.TYPE), //
				new Property(CFProtocolConstants.KEY_MANIFEST_PATH)};

		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	protected String planner;
	protected String wizard;
	protected String applicationType;
	protected ManifestParseTree manifest;
	protected String manifestPath;
	protected List<String> required;

	public Plan(String planner, String wizard, String applicationType, ManifestParseTree manifest, String manifestPath) {
		this.required = new ArrayList<String>();
		this.applicationType = applicationType;
		this.planner = planner;
		this.wizard = wizard;
		this.manifest = manifest;
		this.manifestPath = manifestPath;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_APPLICATION_TYPE)
	public String getApplicationType() {
		return applicationType;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_DEPLOYMENT_PLANNER)
	public String getPlanner() {
		return planner;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_DEPLOYMENT_WIZARD)
	public String getWidget() {
		return wizard;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_MANIFEST_PATH)
	public String getManifestPath() {
		return manifestPath;
	}

	public ManifestParseTree getManifest() {
		return manifest;
	}

	@PropertyDescription(name = Manifest.TYPE)
	public JSONObject getManifestJSON() throws JSONException, InvalidAccessException {
		return manifest.toJSON();
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_REQUIRED)
	public List<String> getRequired() {
		return required;
	}

	public void addRequired(String property) {
		this.required.add(property);
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return null;
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}
}
