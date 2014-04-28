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
package org.eclipse.orion.internal.server.core.metastore;

import java.io.File;

import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The common parent abstract class of the simple implementation of a {@code IMetaStore}.
 * 
 * @author Anthony Hunter
 */
public abstract class SimpleMetaStore implements IMetaStore {

	public final static int NO_ORION_VERSION = -1;
	public final static String ORION_VERSION = "OrionVersion";
	public final static String PROJECT = "project";
	public static final String ROOT = "metastore";
	public final static String USER = "user";
	public final static String WORKSPACE = "workspace";
	/**
	 * Get the Orion version of the simple metadata storage.
	 * 
	 * @return the Orion version.
	 */
	public static int getOrionVersion(File testRootLocation) {
		// Verify we have a valid MetaStore
		int version = NO_ORION_VERSION;
		JSONObject jsonObject = SimpleMetaStoreUtil.readMetaFile(testRootLocation, SimpleMetaStore.ROOT);
		try {
			if (jsonObject != null && jsonObject.has(SimpleMetaStore.ORION_VERSION)) {
				version = jsonObject.getInt(SimpleMetaStore.ORION_VERSION);
			}
		} catch (JSONException e) {
			throw new RuntimeException("SimpleMetaStore.initializeMetaStore: could not read MetaStore.");
		}
		return version;
	}

	private File rootLocation = null;

	/**
	 * Create an instance of a SimpleMetaStore under the provided folder.
	 * @param rootLocation The root location for storing content and metadata on this server.
	 */
	public SimpleMetaStore(File rootLocation) {
		super();
		this.rootLocation = rootLocation;
		initializeMetaStore(rootLocation);
	}

	public File getRootLocation() {
		return rootLocation;
	}

	protected abstract void initializeMetaStore(File rootLocation);
}
