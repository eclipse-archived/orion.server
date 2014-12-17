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

import java.net.URI;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
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
	 * Returns the root location where data files are stored. This is the value of the 
	 * serverworkspace.  This is not necessarily the OSGi instance location.
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
	 * Returns the OSGi instance location for this server.
	 */
	public static IPath getPlatformLocation() {
		return Activator.getDefault().getPlatformLocation();
	}
}
