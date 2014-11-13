/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.events;

import org.eclipse.core.filesystem.IFileStore;

/**
 * Interface for a listener of the Orion file change notification service. The server will notify
 * the listener on create, update and delete events for both files and directories.
 *  
 * @author Anthony Hunter
 */
public interface IFileChangeListener {

	/**
	 * Notification event that a directory has been created.
	 * @param directory the created directory.
	 */
	public void directoryCreated(IFileStore directory);

	/**
	 * Notification event that a directory has been deleted.
	 * @param directory the deleted directory.
	 */
	public void directoryDeleted(IFileStore directory);

	/**
	 * Notification event that a directory has been updated.
	 * @param directory the updated directory.
	 */
	public void directoryUpdated(IFileStore directory);

	/**
	 * Notification event that a file has been created.
	 * @param file the created file.
	 */
	public void fileCreated(IFileStore file);

	/**
	 * Notification event that a file has been deleted.
	 * @param file the deleted file.
	 */
	public void fileDeleted(IFileStore file);

	/**
	 * Notification event that a file has been updated.
	 * @param file the updated file.
	 */
	public void fileUpdated(IFileStore file);

}
