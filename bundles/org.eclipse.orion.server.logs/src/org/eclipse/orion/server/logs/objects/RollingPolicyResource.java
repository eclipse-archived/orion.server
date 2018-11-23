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
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.logs.LogConstants;
import org.json.JSONObject;

import ch.qos.logback.core.rolling.RollingPolicyBase;
import ch.qos.logback.core.rolling.helper.CompressionMode;

@ResourceDescription(type = RollingPolicyResource.TYPE)
public class RollingPolicyResource {
	public static final String RESOURCE = "rollingPolicy"; //$NON-NLS-1$
	public static final String TYPE = "RollingPolicy"; //$NON-NLS-1$

	public RollingPolicyResource(RollingPolicyBase policy) {
		fileNamePattern = policy.getFileNamePattern();
		compressionMode = policy.getCompressionMode();
	}

	protected static ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(LogConstants.KEY_ROLLING_POLICY_FILE_NAME_PATTERN), //
				new Property(LogConstants.KEY_ROLLING_POLICY_COMPRESSION_MODE) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	protected Serializer<JSONObject> jsonSerializer = new JSONSerializer();

	protected String fileNamePattern;
	protected CompressionMode compressionMode;

	@PropertyDescription(name = LogConstants.KEY_ROLLING_POLICY_FILE_NAME_PATTERN)
	public String getFileNamePattern() {
		return fileNamePattern;
	}

	public void setFileNamePattern(String fileNamePattern) {
		this.fileNamePattern = fileNamePattern;
	}

	@PropertyDescription(name = LogConstants.KEY_ROLLING_POLICY_COMPRESSION_MODE)
	public CompressionMode getCompressionMode() {
		return compressionMode;
	}

	public void setCompressionMode(CompressionMode compressionMode) {
		this.compressionMode = compressionMode;
	}

	public JSONObject toJSON() throws URISyntaxException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}
}
