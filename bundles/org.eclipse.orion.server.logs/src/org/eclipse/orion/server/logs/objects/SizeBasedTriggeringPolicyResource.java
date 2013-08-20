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

import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;

@ResourceDescription(type = SizeBasedTriggeringPolicyResource.TYPE)
public class SizeBasedTriggeringPolicyResource extends TriggeringPolicyResource {
	public static final String RESOURCE = "sizeBasedTriggeringPolicy"; //$NON-NLS-1$
	public static final String TYPE = "SizeBasedTriggeringPolicy"; //$NON-NLS-1$

	public SizeBasedTriggeringPolicyResource(SizeBasedTriggeringPolicy<?> policy) {
		this.maxFileSize = policy.getMaxFileSize();
	}

	{
		/* extend base properties */
		Property[] defaultProperties = new Property[] { //
		new Property(LogConstants.KEY_TRIGGERING_POLICY_MAX_FILE_SIZE) };

		Property[] baseProperties = DEFAULT_RESOURCE_SHAPE.getProperties();
		Property[] extendedProperties = new Property[baseProperties.length
				+ defaultProperties.length];

		for (int i = 0; i < baseProperties.length; ++i)
			extendedProperties[i] = baseProperties[i];

		for (int i = baseProperties.length, j = 0; i < extendedProperties.length; ++i, ++j)
			extendedProperties[i] = defaultProperties[j];

		DEFAULT_RESOURCE_SHAPE.setProperties(extendedProperties);
	}

	protected String maxFileSize;

	@PropertyDescription(name = LogConstants.KEY_TRIGGERING_POLICY_MAX_FILE_SIZE)
	public String getMaxFileSize() {
		return maxFileSize;
	}

	public void setMaxFileSize(String maxFileSize) {
		this.maxFileSize = maxFileSize;
	}
}
