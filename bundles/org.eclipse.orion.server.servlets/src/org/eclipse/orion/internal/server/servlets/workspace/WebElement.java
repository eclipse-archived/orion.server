/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.users.OrionScope;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Base class for Eclipse web users, workspaces, and projects.
 */
public class WebElement {
	protected static final IScopeContext scope = new OrionScope();
	protected IEclipsePreferences store;
	protected static final boolean caseSensitive = "macosx".equals(System.getProperty("osgi.os")) ? false : new java.io.File("a").compareTo(new java.io.File("A")) != 0; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	/**
	 * Creates a new user, workspace, or project backed by the given preference store.
	 */
	public WebElement(IEclipsePreferences store) {
		this.store = store;
		try {
			store.sync();
		} catch (BackingStoreException e) {
			//log
		}
	}

	/**
	 * Returns the globally unique id of this element
	 * @return the element id
	 */
	public String getId() {
		return store.get(ProtocolConstants.KEY_ID, null);
	}

	/**
	 * Returns the name of this element
	 * @return the element name
	 */
	public String getName() {
		return store.get(ProtocolConstants.KEY_NAME, null);
	}

	/**
	 * Saves the state of this element to the backing storage.
	 */
	public void save() throws CoreException {
		try {
			//flush directly at root level to workaround equinox bug 389754.
			store.parent().flush();
		} catch (BackingStoreException e) {
			throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, "Error saving state"));
		}
	}

	/**
	 * Sets the globally unique id of this element
	 * @param id the element id
	 */
	public void setId(String id) {
		store.put(ProtocolConstants.KEY_ID, id);
	}

	/**
	 * Sets the name of this element.
	 * @param name the element name
	 */
	public void setName(String name) {
		store.put(ProtocolConstants.KEY_NAME, name);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(Id=" + getId() + ", Name=" + getName() + ")";
	}

	protected static boolean containsUpperCase(String id) {
		for (char c : id.toCharArray()) {
			if (Character.isUpperCase(c)) {
				return true;
			}
		}
		return false;
	}
}
