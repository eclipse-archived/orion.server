/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.core;

import java.net.URI;

/**
 * An alias registry registers mappings of simple strings to file system
 * locations.
 */
public interface IAliasRegistry {
	public void registerAlias(String alias, URI location);

	public void unregisterAlias(String alias);

	public URI lookupAlias(String alias);
}
