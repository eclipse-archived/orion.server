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

}
