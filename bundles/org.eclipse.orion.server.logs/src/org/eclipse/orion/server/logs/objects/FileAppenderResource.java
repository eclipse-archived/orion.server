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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

@ResourceDescription(type = FileAppenderResource.TYPE)
public class FileAppenderResource {
	public static final String RESOURCE = "fileAppender"; //$NON-NLS-1$
	public static final String TYPE = "FileAppender"; //$NON-NLS-1$

	public FileAppenderResource(FileAppender<ILoggingEvent> fileAppender,
			URI baseLocation) {

		this.baseLocation = baseLocation;
		this.name = fileAppender.getName();
		this.isAppend = fileAppender.isAppend();
		this.isPrudent = fileAppender.isPrudent();
		this.isStarted = fileAppender.isStarted();
	}

	protected static ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_NAME), //
				new Property(ProtocolConstants.KEY_LOCATION), //
				new Property(LogConstants.KEY_APPENDER_NAME), //
				new Property(LogConstants.KEY_APPENDER_IS_APPEND), //
				new Property(LogConstants.KEY_APPENDER_IS_PRUDENT), //
				new Property(LogConstants.KEY_APPENDER_IS_STARTED) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}
	protected Serializer<JSONObject> jsonSerializer = new JSONSerializer();
	protected URI baseLocation;

	protected String name;
	protected boolean isAppend;
	protected boolean isPrudent;
	protected boolean isStarted;

	protected URI createUriWithPath(final IPath path) throws URISyntaxException {
		return new URI(baseLocation.getScheme(), baseLocation.getUserInfo(),
				baseLocation.getHost(), baseLocation.getPort(),
				path.toString(), baseLocation.getQuery(),
				baseLocation.getFragment());
	}

	public JSONObject toJSON() throws URISyntaxException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_NAME)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@PropertyDescription(name = LogConstants.KEY_APPENDER_IS_APPEND)
	public boolean isAppend() {
		return isAppend;
	}

	public void setAppend(boolean isAppend) {
		this.isAppend = isAppend;
	}

	@PropertyDescription(name = LogConstants.KEY_APPENDER_IS_PRUDENT)
	public boolean isPrudent() {
		return isPrudent;
	}

	public void setPrudent(boolean isPrudent) {
		this.isPrudent = isPrudent;
	}

	@PropertyDescription(name = LogConstants.KEY_APPENDER_IS_STARTED)
	public boolean isStarted() {
		return isStarted;
	}

	public void setStarted(boolean isStarted) {
		this.isStarted = isStarted;
	}

	public void setBaseLocation(URI baseLocation) {
		this.baseLocation = baseLocation;
	}

	@PropertyDescription(name = ProtocolConstants.KEY_LOCATION)
	public URI getLocation() throws URISyntaxException {
		IPath path = new Path(LogServlet.LOGAPI_URI).append(
				FileAppenderResource.RESOURCE).append(getName());
		return createUriWithPath(path);
	}
}
