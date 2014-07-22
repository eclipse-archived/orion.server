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
package org.eclipse.orion.server.core;

import java.io.File;
import java.net.URI;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.orion.internal.server.core.metastore.*;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class encapsulates information from the server's configuration. Details on where the 
 * configuration is stored and how it is represented is hidden within this class.
 */
public class OrionConfiguration {
	private static final String SECURESTORAGE = "/.metadata/.plugins/org.eclipse.orion.server.user.securestorage/user_store"; //$NON-NLS-1$
	private static final String USERS_PREFS = "/.metadata/.plugins/org.eclipse.orion.server.core/.settings/Users.prefs"; //$NON-NLS-1$
	private static String metaStorePreference = null;

	/**
	 * Returns the currently configured {@link IMetaStore} for this server.
	 * @throws IllegalStateException if the server is not properly configured to have an @link {@link IMetaStore}. 
	 */
	public static IMetaStore getMetaStore() {
		return Activator.getDefault().getMetastore();
	}

	/**
	 * Returns the root location where data files are stored. This is the value of the serverworkspace.
	 * 
	 * @return the root location.
	 */
	public static IFileStore getRootLocation() {
		URI platformLocationURI = Activator.getDefault().getRootLocationURI();
		IFileStore root = null;
		try {
			root = EFS.getStore(platformLocationURI);
		} catch (CoreException e) {
			//this is fatal, we can't access the platform instance location
			throw new Error("Failed to access platform instance location", e); //$NON-NLS-1$
		}
		return root;
	}

	/**
	 * Returns the root location where user data files for the given user are stored. In some
	 * configurations this location might be shared across users, so clients will need to ensure
	 * resulting files are segmented appropriately by user.
	 * @deprecated Use {@link IMetaStore#getUserHome(String)}
	 */
	public static IFileStore getUserHome(String userId) {
		return getMetaStore().getUserHome(userId);
	}

	/** 
	 * Consults the Orion configuration and files on disk if needed to determine which server metadata storage 
	 * should be used.
	 * @return either {@link ServerConstants#CONFIG_META_STORE_LEGACY} or {@link ServerConstants#CONFIG_META_STORE_SIMPLE}
	 */
	public static String getMetaStorePreference() {
		if (metaStorePreference != null) {
			return metaStorePreference;
		}
		// consult the metastore preference 
		String metastore = PreferenceHelper.getString(ServerConstants.CONFIG_META_STORE, "none").toLowerCase(); //$NON-NLS-1$

		if (ServerConstants.CONFIG_META_STORE_SIMPLE.equals(metastore) || ServerConstants.CONFIG_META_STORE_SIMPLE_V2.equals(metastore) || ServerConstants.CONFIG_META_STORE_LEGACY.equals(metastore)) {
			metaStorePreference = metastore;
			return metaStorePreference;
		}

		// metastore preference was not specified by the user.
		try {
			File rootFile = getRootLocation().toLocalFile(EFS.NONE, null);
			File securestorage = new File(rootFile, SECURESTORAGE);
			File users_prefs = new File(rootFile, USERS_PREFS);
			int version = SimpleMetaStore.getOrionVersion(rootFile);
			if (SimpleMetaStoreV1.VERSION == version) {
				// version one of the simple metadata storage
				metaStorePreference = ServerConstants.CONFIG_META_STORE_SIMPLE;
				return metaStorePreference;
			} else if (SimpleMetaStoreV2.VERSION == version) {
				// version two of the simple metadata storage
				metaStorePreference = ServerConstants.CONFIG_META_STORE_SIMPLE_V2;
				return metaStorePreference;
			} else if (securestorage.exists() || users_prefs.exists()) {
				// the metastore preference was not provided and legacy metadata files exist.
				Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
				logger.error("Preference orion.core.metastore was not supplied and legacy files exist, see https://wiki.eclipse.org/Orion/Metadata_migration to migrate to the current version");
				metaStorePreference = ServerConstants.CONFIG_META_STORE_LEGACY;
				return metaStorePreference;
			} else {
				// version two of the simple metadata storage is the default
				metaStorePreference = ServerConstants.CONFIG_META_STORE_SIMPLE_V2;
				return metaStorePreference;
			}
		} catch (CoreException e) {
			//this is fatal, we can't access the root location
			throw new Error("Failed to access platform instance location", e); //$NON-NLS-1$
		}
	}
	
	/**
	 * Returns the OSGi instance location for this server.
	 */
	public static IPath getPlatformLocation() {
		return Activator.getDefault().getPlatformLocation();
	}
}
