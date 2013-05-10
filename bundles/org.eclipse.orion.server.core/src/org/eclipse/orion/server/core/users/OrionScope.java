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
package org.eclipse.orion.server.core.users;

import org.eclipse.core.internal.preferences.AbstractScope;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.server.core.metastore.IMetaStore;

/**
 * @deprecated Use {@link IMetaStore} instead.
 */
public class OrionScope extends AbstractScope {
	/**
	 * String constant (value of <code>"configuration"</code>) used for the 
	 * scope name for the orion preference scope.
	 */
	public static final String SCOPE = "eclipseweb"; //$NON-NLS-1$

	/**
	 * Create and return a new configuration scope instance.
	 */
	public OrionScope() {
		super();
	}

	/*
	 * @see org.eclipse.core.runtime.preferences.IScopeContext#getName()
	 */
	public String getName() {
		return SCOPE;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.preferences.IScopeContext#getNode(java.lang.String)
	 */
	public IEclipsePreferences getNode(String qualifier) {
		return super.getNode(qualifier);
	}

	/*
	 * @see org.eclipse.core.runtime.preferences.IScopeContext#getLocation()
	 */
	public IPath getLocation() {
		return null;
	}

}
