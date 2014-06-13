/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets;

import java.util.EventObject;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.internal.server.servlets.IFileStoreModificationListener.ChangeType;

/**
 * Describes an edit change to a file or folder. 
 */
public class ChangeEvent extends EventObject {
	private static final long serialVersionUID = -4402869221399239402L;

	final IFileStoreModificationListener.ChangeType type;

	final IFileStore modified;
	final IFileStore start;

	public ChangeEvent(Object source, IFileStoreModificationListener.ChangeType type, IFileStore modified) {
		this(source, type, modified, null);
	}

	public ChangeEvent(Object source, IFileStoreModificationListener.ChangeType type, IFileStore modified, IFileStore moveStart) {
		super(source);
		this.type = type;
		this.modified = modified;
		this.start = moveStart;
	}

	public ChangeType getChangeType() {
		return type;
	}

	/**
	 * Get the item that was changed on disk. 
	 */
	public IFileStore getModifiedItem() {
		return modified;
	}

	/**
	 * For a move, return the initial location. For a copy, return the initial source. 
	 * For other change types, the return value is <code>null</code>.
	 */
	public IFileStore getInitialLocation() {
		return start;
	}
}