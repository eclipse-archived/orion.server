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

package org.eclipse.orion.server.logs.objects;

import java.net.URISyntaxException;

import org.eclipse.orion.server.core.resources.JSONSerializer;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.Serializer;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONObject;

@ResourceDescription(type = TriggeringPolicyResource.TYPE)
public class TriggeringPolicyResource {
	public static final String RESOURCE = "triggeringPolicy"; //$NON-NLS-1$
	public static final String TYPE = "TriggeringPolicy"; //$NON-NLS-1$

	protected static ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] {};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	protected Serializer<JSONObject> jsonSerializer = new JSONSerializer();

	public JSONObject toJSON() throws URISyntaxException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}
}
