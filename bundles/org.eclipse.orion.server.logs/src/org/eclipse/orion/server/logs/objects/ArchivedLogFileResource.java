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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.JSONSerializer;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.Serializer;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.logs.LogConstants;
import org.eclipse.orion.server.logs.servlets.LogServlet;
import org.json.JSONObject;

@ResourceDescription(type = ArchivedLogFileResource.TYPE)
public class ArchivedLogFileResource {
	public static final String RESOURCE = "archivedLogFile"; //$NON-NLS-1$
	public static final String TYPE = "ArchivedLogFile"; //$NON-NLS-1$

	public ArchivedLogFileResource(
			RollingFileAppenderResource rollingFileAppender, File logFile) {

		this.rollingFileAppender = rollingFileAppender;
		this.baseLocation = rollingFileAppender.baseLocation;
		this.name = logFile.getName();
	}

	protected static ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_NAME), //
				new Property(ProtocolConstants.KEY_LOCATION), //
				new Property(LogConstants.KEY_ROLLING_FILE_APPENDER_LOCATION) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	protected Serializer<JSONObject> jsonSerializer = new JSONSerializer();
	protected URI baseLocation;

	protected String name;
	protected RollingFileAppenderResource rollingFileAppender;

	@PropertyDescription(name = ProtocolConstants.KEY_NAME)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public RollingFileAppenderResource getRollingFileAppender() {
		return rollingFileAppender;
	}

	public void setRollingFileAppender(
			RollingFileAppenderResource rollingFileAppender) {
		this.rollingFileAppender = rollingFileAppender;
	}

	protected URI createUriWithPath(final IPath path) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getUserInfo(),
				baseLocation.getHost(), baseLocation.getPort(),
				path.toString(), baseLocation.getQuery(),
				baseLocation.getFragment());
	}

	public JSONObject toJSON() throws URISyntaxException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	public void setBaseLocation(URI baseLocation) {
		this.baseLocation = baseLocation;
	}

	@PropertyDescription(name = LogConstants.KEY_ROLLING_FILE_APPENDER_LOCATION)
	public URI getRollingFileAppenderLocation() throws URISyntaxException {
		return getRollingFileAppender().getLocation();
	}

	@PropertyDescription(name = ProtocolConstants.KEY_LOCATION)
	public URI getLocation() throws URISyntaxException {
		IPath path = new Path(LogServlet.LOGAPI_URI)
				.append(RollingFileAppenderResource.RESOURCE)
				.append(getRollingFileAppender().getName()).append(getName());
		return createUriWithPath(path);
	}
}
