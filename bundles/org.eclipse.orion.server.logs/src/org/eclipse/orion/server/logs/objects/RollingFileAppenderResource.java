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
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.logs.LogConstants;
import org.eclipse.orion.server.logs.servlets.LogServlet;
import org.json.JSONArray;
import org.json.JSONObject;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.TriggeringPolicy;

@ResourceDescription(type = RollingFileAppenderResource.TYPE)
public class RollingFileAppenderResource extends FileAppenderResource {
	public static final String RESOURCE = "rollingFileAppender"; //$NON-NLS-1$
	public static final String TYPE = "RollingFileAppender"; //$NON-NLS-1$

	public RollingFileAppenderResource(
			FileAppender<ILoggingEvent> fileAppender, URI baseLocation) {
		super(fileAppender, baseLocation);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public RollingFileAppenderResource(
			RollingFileAppender<ILoggingEvent> rollingFileAppender,
			URI baseLocation) {

		super(rollingFileAppender, baseLocation);
		RollingPolicy rollingPolicy = rollingFileAppender.getRollingPolicy();
		TriggeringPolicy<ILoggingEvent> triggeringPolicy = rollingFileAppender
				.getTriggeringPolicy();

		if (rollingPolicy instanceof TimeBasedRollingPolicy<?>) {
			TimeBasedRollingPolicy<ILoggingEvent> policy = (TimeBasedRollingPolicy<ILoggingEvent>) rollingPolicy;
			this.rollingPolicy = new TimeBasedRollingPolicyResource(policy);
		}

		if (rollingPolicy instanceof FixedWindowRollingPolicy) {
			FixedWindowRollingPolicy policy = (FixedWindowRollingPolicy) rollingPolicy;
			this.rollingPolicy = new FixedWindowRollingPolicyResource(policy);
		}

		if (triggeringPolicy instanceof SizeBasedTriggeringPolicy) {
			SizeBasedTriggeringPolicy policy = (SizeBasedTriggeringPolicy) triggeringPolicy;
			this.triggeringPolicy = new SizeBasedTriggeringPolicyResource(
					policy);
		}
	}

	{
		/* extend base properties */
		Property[] defaultProperties = new Property[] { //
		new Property(LogConstants.KEY_APPENDER_ROLLING_POLICY), //
				new Property(LogConstants.KEY_APPENDER_TRIGGERING_POLICY), //
				new Property(LogConstants.KEY_APPENDER_ARCHIVED_LOG_FILES) };

		Property[] baseProperties = DEFAULT_RESOURCE_SHAPE.getProperties();
		Property[] extendedProperties = new Property[baseProperties.length
				+ defaultProperties.length];

		for (int i = 0; i < baseProperties.length; ++i)
			extendedProperties[i] = baseProperties[i];

		for (int i = baseProperties.length, j = 0; i < extendedProperties.length; ++i, ++j)
			extendedProperties[i] = defaultProperties[j];

		DEFAULT_RESOURCE_SHAPE.setProperties(extendedProperties);
	}

	protected RollingPolicyResource rollingPolicy;
	protected TriggeringPolicyResource triggeringPolicy;
	protected List<ArchivedLogFileResource> archivedLogFiles;

	public RollingPolicyResource getRollingPolicy() {
		return rollingPolicy;
	}

	@PropertyDescription(name = LogConstants.KEY_APPENDER_ROLLING_POLICY)
	public JSONObject getRollingPolicyJSON() throws URISyntaxException {
		if (rollingPolicy != null)
			return rollingPolicy.toJSON();

		return null;
	}

	public List<ArchivedLogFileResource> getArchivedLogFiles() {
		return archivedLogFiles;
	}

	@PropertyDescription(name = LogConstants.KEY_APPENDER_ARCHIVED_LOG_FILES)
	public JSONArray getArchivedLogFilesJSON() throws URISyntaxException {
		if (archivedLogFiles == null)
			return null;

		JSONArray logFiles = new JSONArray();
		for (ArchivedLogFileResource logFile : archivedLogFiles)
			logFiles.put(logFile.toJSON());

		return logFiles;
	}

	public void setArchivedLogFiles(
			List<ArchivedLogFileResource> archivedLogFiles) {
		this.archivedLogFiles = archivedLogFiles;
	}

	public void setRollingPolicy(RollingPolicyResource rollingPolicy) {
		this.rollingPolicy = rollingPolicy;
	}

	public TriggeringPolicyResource getTriggeringPolicy() {
		return triggeringPolicy;
	}

	@PropertyDescription(name = LogConstants.KEY_APPENDER_TRIGGERING_POLICY)
	public JSONObject getTriggeringPolicyJSON() throws URISyntaxException {
		if (triggeringPolicy != null)
			return triggeringPolicy.toJSON();

		return null;
	}

	public void setTriggeringPolicy(TriggeringPolicyResource triggeringPolicy) {
		this.triggeringPolicy = triggeringPolicy;
	}

	@Override
	@PropertyDescription(name = ProtocolConstants.KEY_LOCATION)
	public URI getLocation() throws URISyntaxException {
		IPath path = new Path(LogServlet.LOGAPI_URI).append(
				RollingFileAppenderResource.RESOURCE).append(getName());
		return createUriWithPath(path);
	}
}
