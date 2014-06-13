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


/**
 * A listener class that is informed of changes in the local filesystem caused by edit actions in 
 * the Orion web IDE. 
 */
public interface IFileStoreModificationListener {

	/**
	 * Indicates the nature of a change. 
	 */
	public static enum ChangeType {
		/**
		 * Copy of a file/folder to a new location. 
		 * {@link IFileStoreModificationListener.ChangeEvent#getModifiedItem()} will 
		 * return the new file/directory, 
		 * while {@link IFileStoreModificationListener.ChangeEvent#getInitialLocation()}
		 * will return where the file came from. 
		 */
		COPY_INTO,

		/**
		 * Deletion of an item. 
		 * {@link IFileStoreModificationListener.ChangeEvent#getModifiedItem()} will 
		 * return the deleted item. 
		 */
		DELETE,

		/**
		 * Creation of a directory.  
		 * {@link IFileStoreModificationListener.ChangeEvent#getModifiedItem()} will 
		 * return the new directory.  
		 */
		MKDIR,

		/**
		 * Move of an item from one location to another.   
		 * {@link IFileStoreModificationListener.ChangeEvent#getModifiedItem()} will 
		 * return the new location, while 
		 * {@link IFileStoreModificationListener.ChangeEvent#getInitialLocation()} will
		 * return the original location. 
		 */
		MOVE,

		/**
		 * Content written to a file. 
		 * {@link IFileStoreModificationListener.ChangeEvent#getModifiedItem()} 
		 * will return the file written to. 
		 */
		WRITE,

		/**
		 * Attributes of a file/folder changed.  
		 * {@link IFileStoreModificationListener.ChangeEvent#getModifiedItem()} 
		 * will return the modified item. 
		 */
		PUTINFO
	}

	/**
	 * Called to indicate that a change has occurred. 
	 */
	public void changed(ChangeEvent event);
}
