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

import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;

@ResourceDescription(type = FixedWindowRollingPolicyResource.TYPE)
public class FixedWindowRollingPolicyResource extends RollingPolicyResource {
	public static final String RESOURCE = "fixedWindowRollingPolicy"; //$NON-NLS-1$
	public static final String TYPE = "FixedWindowRollingPolicy"; //$NON-NLS-1$

	public FixedWindowRollingPolicyResource(FixedWindowRollingPolicy policy) {
		super(policy);

		minIndex = policy.getMinIndex();
		maxIndex = policy.getMaxIndex();
	}

	{
		/* extend base properties */
		Property[] defaultProperties = new Property[] { //
		new Property(LogConstants.KEY_ROLLING_POLICY_MIN_INDEX), //
				new Property(LogConstants.KEY_ROLLING_POLICY_MAX_INDEX) };

		Property[] baseProperties = DEFAULT_RESOURCE_SHAPE.getProperties();
		Property[] extendedProperties = new Property[baseProperties.length
				+ defaultProperties.length];

		for (int i = 0; i < baseProperties.length; ++i)
			extendedProperties[i] = baseProperties[i];

		for (int i = baseProperties.length, j = 0; i < extendedProperties.length; ++i, ++j)
			extendedProperties[i] = defaultProperties[j];

		DEFAULT_RESOURCE_SHAPE.setProperties(extendedProperties);
	}

	protected int minIndex;
	protected int maxIndex;

	@PropertyDescription(name = LogConstants.KEY_ROLLING_POLICY_MIN_INDEX)
	public int getMinIndex() {
		return minIndex;
	}

	public void setMinIndex(int minIndex) {
		this.minIndex = minIndex;
	}

	@PropertyDescription(name = LogConstants.KEY_ROLLING_POLICY_MAX_INDEX)
	public int getMaxIndex() {
		return maxIndex;
	}

	public void setMaxIndex(int maxIndex) {
		this.maxIndex = maxIndex;
	}
}
