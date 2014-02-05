/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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

	protected static final List<String> EMPTY = Collections.emptyList();

	// List of operations that can be performed on the properties list
	public enum OperationType {
		CREATE, UPDATE, DELETE
	};

	private String fullName;
	private String id;
	private final Map<String, OperationType> operations = Collections.synchronizedMap(new HashMap<String, OperationType>());
	private final Map<String, String> properties = Collections.synchronizedMap(new HashMap<String, String>());

	/**
	 * Creates a new empty metadata object.
	 */
	public MetadataInfo() {
		super();
	}

	/** 
	 * After this object has been persisted, calling flush clears the list of operations 
	 * performed on the properties of this object since the last read.
	 */
	public void flush() {
		operations.clear();
	}

	/**
	 * Returns the full, human readable name of this object. This is typically a name chosen
	 * by the end user for this particular user, workspace or project. There is no guarantee
	 * of uniqueness across instances.
	 * @return the full name of this metadata object
	 */
	public String getFullName() {
		return fullName;
	}

	/**
	 * Returns the value of the persistent property of this metadata object identified
	 * by the given key, or <code>null</code> if this resource has no such property.
	 *
	 * @param key the qualified name of the property
	 * @return the string value of the property, 
	 *     or <code>null</code> if this resource has no such property
	 */
	public String getProperty(String key) {
		return properties.get(key);
	}

	/**
	 * Returns a read-only map of the keys and operations performed on the properties of this object
	 * since the last read.
	 */
	public Map<String, OperationType> getOperations() {
		return Collections.unmodifiableMap(operations);
	}

	/**
	 * Returns a read-only map of the keys and values stored in this object.
	 */
	public Map<String, String> getProperties() {
		return Collections.unmodifiableMap(properties);
	}

	/**
	 * Returns the globally unique id of this metadata object.
	 * @return the id of this object
	 */
	public String getUniqueId() {
		return id;
	}

	/**
	 * Sets the full, human readable name of this object. This is typically a name chosen
	 * by the end user for this particular user, workspace or project. There is no guarantee
	 * of uniqueness across instances.
	 * @param fullName the fullName to set
	 */
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	/**
	 * Sets the value of the persistent property of this metadata object identified
	 * by the given key. If the supplied value is <code>null</code>,
	 * the persistent property is removed from this resource.
	 * <p>
	 * Persistent properties are intended to be used to store specific information 
	 * about this metadata object that should be persisted.
	 * The value of a persistent property is a string that must be short -
	 * 2KB or less in length.
	 * </p>
	 *
	 * @param key the qualified name of the property
	 * @param value the string value of the property, 
	 *     or <code>null</code> if the property is to be removed
	 * @return The previous value associated with this key, or <code>null</code>
	 *   if there was previously no value associated with the key.
	 */
	public String setProperty(String key, String value) {
		if (value == null) {
			if (properties.containsKey(key)) {
				operations.put(key, OperationType.DELETE);
			}
			return properties.remove(key);
		} else {
			if (properties.containsKey(key)) {
				operations.put(key, OperationType.UPDATE);
			} else {
				operations.put(key, OperationType.CREATE);
			}
			return properties.put(key, value);
		}
	}

	/**
	 * Sets the unique id of this metadata object. The id must be unique
	 * across all metadata objects of any given subclass of this class.
	 * The id is not guaranteed to be globally unique across all server
	 * instances.
	 * @param id the unique id of this object
	 */
	public void setUniqueId(String id) {
		this.id = id;
	}

}
