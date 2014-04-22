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
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class encapsulates information from the server's configuration. Details on where the 
 * configuration is stored and how it is represented is hidden within this class.
 */
public class OrionConfiguration {
	private static final String SECURESTORAGE = "/.metadata/.plugins/org.eclipse.orion.server.user.securestorage/user_store";
	private static final String USERS_PREFS = "/.metadata/.plugins/org.eclipse.orion.server.core/.settings/Users.prefs";
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
	 */
	public static IFileStore getUserHome(String userId) {
		IFileStore root = getRootLocation();
		String layout = getFileLayout();

		if (ServerConstants.CONFIG_FILE_LAYOUT_USERTREE.equals(layout) && userId != null) { //$NON-NLS-1$
			//the user-tree layout organises projects by the user who created it
			String userPrefix = userId.substring(0, Math.min(2, userId.length()));
			return root.getChild(userPrefix).getChild(userId);
		}
		//the layout is a flat list of projects at the root
		return root;
	}

	/**
	 * Returns the file layout used on the Orion server. The legacy meta store supports flat 
	 * or userTree file layout. The simple meta store only supports userTree file layout.
	 * @return either {@link ServerConstants#CONFIG_FILE_LAYOUT_FLAT} or {@link ServerConstants#CONFIG_FILE_LAYOUT_USERTREE}
	 */
	public static String getFileLayout() {
		// consult layout preference
		String layout = PreferenceHelper.getString(ServerConstants.CONFIG_FILE_LAYOUT, ServerConstants.CONFIG_FILE_LAYOUT_FLAT).toLowerCase(); //$NON-NLS-1$
		// consult the metastore preference 
		String metastore = getMetaStorePreference();

		if (metastore.equals(ServerConstants.CONFIG_META_STORE_SIMPLE) || layout.equals(ServerConstants.CONFIG_FILE_LAYOUT_USERTREE)) {
			return ServerConstants.CONFIG_FILE_LAYOUT_USERTREE;
		} else {
			return ServerConstants.CONFIG_FILE_LAYOUT_FLAT;
		}
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
		
		if (metastore.equals(ServerConstants.CONFIG_META_STORE_SIMPLE) || metastore.equals(ServerConstants.CONFIG_META_STORE_LEGACY)) {
			metaStorePreference = metastore;
			return metaStorePreference;
		}
		
		// metastore preference was not specified by the user.
		try {
			File rootFile = getRootLocation().toLocalFile(EFS.NONE, null);
			File securestorage = new File(rootFile, SECURESTORAGE);
			File users_prefs = new File(rootFile, USERS_PREFS);
			if (securestorage.exists() || users_prefs.exists()) {
				// the metastore preference was not provided and legacy metadata files exist.
				Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
				logger.error("Preference orion.core.metastore was not supplied and legacy files exist, see https://wiki.eclipse.org/Orion/Metadata_migration to migrate to the current version");
				metaStorePreference = ServerConstants.CONFIG_META_STORE_LEGACY;
				return metaStorePreference;
			} else {
				metaStorePreference = ServerConstants.CONFIG_META_STORE_SIMPLE;
				return metaStorePreference;
			}
		} catch (CoreException e) {
			//this is fatal, we can't access the root location
			throw new Error("Failed to access platform instance location", e); //$NON-NLS-1$
		}
	}
}
