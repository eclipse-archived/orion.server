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
package org.eclipse.orion.server.core.metastore;

import java.util.*;

/**
 * A snapshot of information about an entity of the Orion metadata, such as a user,
 * a workspace, or a project. The information in this structure is disconnected from any particular backing 
 * store. Changes to this object are not persisted without invoking a method on @link {@link IMetaStore}.
 */
public class MetadataInfo {

	private String id;
	private final Map<String, String> properties = Collections.synchronizedMap(new HashMap<String, String>());

	/**
	 * Creates a new empty metadata object.
	 */
	public MetadataInfo() {
		super();
	}

	/**
	 * Returns the globally unique id of this metadata object.
	 * @return the id of this object
	 */
	public String getUID() {
		return id;
	}

	public String getProperty(String key) {
		return properties.get(key);
	}

	public void setProperty(String key, String value) {
		properties.put(key, value);
	}

	public void setUID(String id) {
		this.id = id;
	}

}
