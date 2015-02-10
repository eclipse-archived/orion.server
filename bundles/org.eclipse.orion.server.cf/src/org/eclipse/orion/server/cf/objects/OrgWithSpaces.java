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

import java.util.Iterator;
import java.util.List;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.*;

@ResourceDescription(type = Org.TYPE)
public class OrgWithSpaces extends Org {

	protected static final ResourceShape ORGWITHSPACES_RESOURCE_SHAPE = new ResourceShape();
	{
		ORGWITHSPACES_RESOURCE_SHAPE.setProperties(DEFAULT_RESOURCE_SHAPE.getProperties());
		ORGWITHSPACES_RESOURCE_SHAPE.addProperty(new Property(CFProtocolConstants.KEY_SPACES));
	}

	private List<Space> spaces;

	public OrgWithSpaces() {
		super();
	}

	public List<Space> getSpaces() {
		return spaces;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_SPACES)
	private JSONArray getSpacesJSONArray() {
		try {
			JSONArray spacesJSONArray = new JSONArray();
			for (Iterator<Space> iterator = spaces.iterator(); iterator.hasNext();) {
				spacesJSONArray.put(iterator.next().toJSON());
			}
			return spacesJSONArray;
		} catch (JSONException e) {
			return null;
		}
	}

	public void setSpaces(List<Space> spaces) {
		this.spaces = spaces;
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		return jsonSerializer.serialize(this, ORGWITHSPACES_RESOURCE_SHAPE);
	}

}
