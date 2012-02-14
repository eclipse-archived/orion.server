/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class GitObject {

	protected URI cloneLocation;
	protected Repository db;
	private StoredConfig cfg;

	GitObject(URI cloneLocation, Repository db) {
		this.cloneLocation = cloneLocation;
		this.db = db;
	}

	StoredConfig getConfig() {
		if (this.cfg == null) {
			this.cfg = db.getConfig();
		}
		return this.cfg;
	}

	abstract protected String getType();

	abstract protected URI getLocation() throws URISyntaxException;

	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject result = new JSONObject();
		result.put(ProtocolConstants.KEY_TYPE, getType());
		result.put(ProtocolConstants.KEY_LOCATION, getLocation());
		result.put(GitConstants.KEY_CLONE, cloneLocation);
		return result;
	}
}
