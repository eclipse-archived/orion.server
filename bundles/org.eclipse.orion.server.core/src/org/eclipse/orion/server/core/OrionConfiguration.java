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
package org.eclipse.orion.server.core;

import java.net.URI;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.orion.server.core.metastore.IMetaStore;

/**
 * This class encapsulates information from the server's configuration. Details on where the 
 * configuration is stored and how it is represented is hidden within this class.
 */
public class OrionConfiguration {
	/**
	 * Returns the currently configured {@link IMetaStore} for this server.
	 * @throws IllegalStateException if the server is not properly configured to have an @link {@link IMetaStore}. 
	 */
	public static IMetaStore getMetaStore() {
		return Activator.getDefault().getMetastore();
	}

	/**
	 * Returns the root location where user data files for the given user are stored. In some
	 * configurations this location might be shared across users, so clients will need to ensure
	 * resulting files are segmented appropriately by user.
	 */
	public static IFileStore getUserHome(String userId) {
		URI platformLocationURI = Activator.getDefault().getRootLocationURI();
		IFileStore root = null;
		try {
			root = EFS.getStore(platformLocationURI);
		} catch (CoreException e) {
			//this is fatal, we can't access the platform instance location
			throw new Error("Failed to access platform instance location", e); //$NON-NLS-1$
		}

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
		String metastore = PreferenceHelper.getString(ServerConstants.CONFIG_META_STORE, ServerConstants.CONFIG_META_STORE_LEGACY).toLowerCase(); //$NON-NLS-1$

		if (metastore.equals(ServerConstants.CONFIG_META_STORE_SIMPLE) || layout.equals(ServerConstants.CONFIG_FILE_LAYOUT_USERTREE)) {
			return ServerConstants.CONFIG_FILE_LAYOUT_USERTREE;
		} else {
			return ServerConstants.CONFIG_FILE_LAYOUT_FLAT;
		}
	}
}
