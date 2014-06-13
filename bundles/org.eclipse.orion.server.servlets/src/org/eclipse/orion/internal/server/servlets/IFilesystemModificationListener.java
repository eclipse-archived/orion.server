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

import org.eclipse.core.filesystem.IFileStore;

/**
 * A listener class that is informed of changes in the local filesystem caused by edit actions in 
 * the Orion web IDE. 
 */
public interface IFilesystemModificationListener {

	/**
	 * Indicates the nature of a change. 
	 */
	public static enum ChangeType {
		/**
		 * Copy of a file/folder to a new location. 
		 * {@link IFilesystemModificationListener.ChangeEvent#getModifiedItem()} will 
		 * return the new file/directory, 
		 * while {@link IFilesystemModificationListener.ChangeEvent#getInitialLocation()}
		 * will return where the file came from. 
		 */
		COPY_INTO,

		/**
		 * Deletion of an item. 
		 * {@link IFilesystemModificationListener.ChangeEvent#getModifiedItem()} will 
		 * return the deleted item. 
		 */
		DELETE,

		/**
		 * Creation of a directory.  
		 * {@link IFilesystemModificationListener.ChangeEvent#getModifiedItem()} will 
		 * return the new directory.  
		 */
		MKDIR,

		/**
		 * Move of an item from one location to another.   
		 * {@link IFilesystemModificationListener.ChangeEvent#getModifiedItem()} will 
		 * return the new location, while 
		 * {@link IFilesystemModificationListener.ChangeEvent#getInitialLocation()} will
		 * return the original location. 
		 */
		MOVE,

		/**
		 * Content written to a file. 
		 * {@link IFilesystemModificationListener.ChangeEvent#getModifiedItem()} 
		 * will return the file written to. 
		 */
		WRITE,

		/**
		 * Attributes of a file/folder changed.  
		 * {@link IFilesystemModificationListener.ChangeEvent#getModifiedItem()} 
		 * will return the modified item. 
		 */
		PUTINFO
	}

	/**
	 * Describes an edit change to a file or folder. 
	 */
	public static class ChangeEvent {
		final IFilesystemModificationListener.ChangeType type;

		final IFileStore modified;
		final IFileStore start;

		public ChangeEvent(IFilesystemModificationListener.ChangeType type, IFileStore modified) {
			this(type, modified, null);
		}

		public ChangeEvent(IFilesystemModificationListener.ChangeType type, IFileStore modified, IFileStore moveStart) {
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

	/**
	 * Called to indicate that a change has occurred. 
	 */
	public void changed(ChangeEvent event);
}
