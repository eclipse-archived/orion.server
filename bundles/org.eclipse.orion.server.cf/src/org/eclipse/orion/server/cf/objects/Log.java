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

import org.eclipse.orion.server.core.ProtocolConstants;

import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Log.TYPE)
public class Log extends CFObject {
	public static final String RESOURCE = "logs"; //$NON-NLS-1$
	public static final String TYPE = "Log"; //$NON-NLS-1$

	private String name;
	private String contents;
	private String application;
	private String size;
	private URI location;

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION),//
				new Property(CFProtocolConstants.KEY_NAME),//
				new Property(CFProtocolConstants.KEY_CONTENTS), //
				new Property(CFProtocolConstants.KEY_APPLICATION), //
				new Property(CFProtocolConstants.KEY_SIZE) //
		};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	/**
	 * @param application Application name
	 * @param name Log file name
	 */
	public Log(String application, String name) {
		super();
		this.name = name;
		this.application = application;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_APPLICATION)
	public String getApplication() {
		return application;
	}

	public void setApplication(String application) {
		this.application = application;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_NAME)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_CONTENTS)
	public String getContents() {
		return contents;
	}

	public void setContents(String contents) {
		this.contents = contents;
	}

	@PropertyDescription(name = CFProtocolConstants.KEY_SIZE)
	public String getSize() {
		return size;
	}

	public void setSize(String size) {
		this.size = size;
	}

	public void setLocation(URI location) {
		this.location = location;
	}

	@Override
	@PropertyDescription(name = ProtocolConstants.KEY_LOCATION)
	protected URI getLocation() throws URISyntaxException {
		return location;
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

}
