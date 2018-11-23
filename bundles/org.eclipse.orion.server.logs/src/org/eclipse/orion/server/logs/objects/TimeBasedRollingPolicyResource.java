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

import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.logs.LogConstants;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

@ResourceDescription(type = TimeBasedRollingPolicyResource.TYPE)
public class TimeBasedRollingPolicyResource extends RollingPolicyResource {
	public static final String RESOURCE = "timeBasedRollingPolicy"; //$NON-NLS-1$
	public static final String TYPE = "TimeBasedRollingPolicy"; //$NON-NLS-1$

	public TimeBasedRollingPolicyResource(
			TimeBasedRollingPolicy<ILoggingEvent> policy) {

		super(policy);
		maxHistory = policy.getMaxHistory();
		cleanHistoryOnStart = policy.isCleanHistoryOnStart();
	}

	{
		/* extend base properties */
		Property[] defaultProperties = new Property[] { //
		new Property(LogConstants.KEY_ROLLING_POLICY_CLEAN_HISTORY_ON_START), //
				new Property(LogConstants.KEY_ROLLING_POLICY_MAX_HISTORY) };

		Property[] baseProperties = DEFAULT_RESOURCE_SHAPE.getProperties();
		Property[] extendedProperties = new Property[baseProperties.length
				+ defaultProperties.length];

		for (int i = 0; i < baseProperties.length; ++i)
			extendedProperties[i] = baseProperties[i];

		for (int i = baseProperties.length, j = 0; i < extendedProperties.length; ++i, ++j)
			extendedProperties[i] = defaultProperties[j];

		DEFAULT_RESOURCE_SHAPE.setProperties(extendedProperties);
	}

	protected int maxHistory;
	protected boolean cleanHistoryOnStart;

	@PropertyDescription(name = LogConstants.KEY_ROLLING_POLICY_MAX_HISTORY)
	public int getMaxHistory() {
		return maxHistory;
	}

	public void setMaxHistory(int maxHistory) {
		this.maxHistory = maxHistory;
	}

	@PropertyDescription(name = LogConstants.KEY_ROLLING_POLICY_CLEAN_HISTORY_ON_START)
	public boolean isCleanHistoryOnStart() {
		return cleanHistoryOnStart;
	}

	public void setCleanHistoryOnStart(boolean cleanHistoryOnStart) {
		this.cleanHistoryOnStart = cleanHistoryOnStart;
	}
}
