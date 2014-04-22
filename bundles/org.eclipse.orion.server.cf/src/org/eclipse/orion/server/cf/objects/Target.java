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

import java.net.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Target.TYPE)
public class Target extends CFObject {

	public static final String RESOURCE = "target"; //$NON-NLS-1$
	public static final String TYPE = "Target"; //$NON-NLS-1$

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(CFProtocolConstants.KEY_URL), //
				new Property(Space.TYPE), //
				new Property(Org.TYPE) //
		};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	private Org org;
	private Space space;
	private Cloud cloud;

	public Target(Cloud cloud) {
		this.cloud = cloud;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return null;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_URL)
	public URL getUrl() {
		return cloud.getUrl();
	}

	public URL getManageUrl() {
		return cloud.getManageUrl();
	}

	public Cloud getCloud() {
		return cloud;
	}

	public Org getOrg() {
		return org;
	}

	@PropertyDescription(name = Org.TYPE)
	private JSONObject getOrgJSON() {
		try {
			return org == null ? null : org.toJSON();
		} catch (JSONException e) {
			return null;
		}
	}

	public void setOrg(Org org) {
		this.org = org;
	}

	public Space getSpace() {
		return space;
	}

	@PropertyDescription(name = Space.TYPE)
	private JSONObject getSpaceJSON() {
		try {
			return space == null ? null : space.toJSON();
		} catch (JSONException e) {
			return null;
		}
	}

	public void setSpace(Space space) {
		this.space = space;
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cloud == null) ? 0 : cloud.hashCode());
		result = prime * result + ((org == null) ? 0 : org.hashCode());
		result = prime * result + ((space == null) ? 0 : space.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Target other = (Target) obj;
		if (cloud == null) {
			if (other.cloud != null)
				return false;
		} else if (!cloud.equals(other.cloud))
			return false;
		if (org == null) {
			if (other.org != null)
				return false;
		} else if (!org.equals(other.org))
			return false;
		if (space == null) {
			if (other.space != null)
				return false;
		} else if (!space.equals(other.space))
			return false;
		return true;
	}
}
