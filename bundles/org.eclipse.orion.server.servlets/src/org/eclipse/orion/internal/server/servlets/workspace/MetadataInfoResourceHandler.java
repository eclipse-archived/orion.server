/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.MetadataInfo;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Base class for resource handlers that process subclasses of {@link WebElement}.
 */
public abstract class MetadataInfoResourceHandler<T extends MetadataInfo> extends ServletResourceHandler<T> {

	public static JSONObject toJSON(MetadataInfo element) {
		JSONObject result = new JSONObject();
		try {
			result.put(ProtocolConstants.KEY_ID, element.getUniqueId());
			result.put(ProtocolConstants.KEY_NAME, element.getFullName());
		} catch (JSONException e) {
			//cannot happen, we know keys and values are valid
		}
		return result;
	}

	protected IMetaStore getMetaStore() {
		return OrionConfiguration.getMetaStore();
	}

}
