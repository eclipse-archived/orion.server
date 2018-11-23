/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.objects;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.JSONSerializer;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.Serializer;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class GitObject {
	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { new Property(ProtocolConstants.KEY_LOCATION), new Property(GitConstants.KEY_CLONE) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	protected Serializer<JSONObject> jsonSerializer;

	protected URI cloneLocation;
	protected Repository db;
	private StoredConfig cfg;

	private GitObject() {
		this.jsonSerializer = new JSONSerializer();
	}

	GitObject(URI cloneLocation, Repository db) {
		this();
		this.cloneLocation = cloneLocation;
		this.db = db;
	}

	StoredConfig getConfig() {
		if (this.cfg == null) {
			this.cfg = db.getConfig();
		}
		return this.cfg;
	}

	@PropertyDescription(name = ProtocolConstants.KEY_LOCATION)
	abstract protected URI getLocation() throws URISyntaxException;

	@PropertyDescription(name = GitConstants.KEY_CLONE)
	public URI getCloneLocation() {
		return cloneLocation;
	}

	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}
}
