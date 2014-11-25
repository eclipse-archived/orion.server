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
import org.eclipse.orion.server.core.metastore.ProjectInfo;

/**
 * Interface for a listener of the Orion file change notification service. The server will notify
 * the listener on create, update and delete events for both files and directories within projects
 * in the server workspace.
 *  
 * @author Anthony Hunter
 */
public interface IFileChangeListener {

	/**
	 * Notification event that a directory has been created.
	 * @param directory the created directory.
	 * @param projectInfo the project the owns the created directory.
	 */
	public void directoryCreated(IFileStore directory, ProjectInfo projectInfo);

	/**
	 * Notification event that a directory has been deleted.
	 * @param directory the deleted directory.
	 * @param projectInfo the project the owns the deleted directory.
	 */
	public void directoryDeleted(IFileStore directory, ProjectInfo projectInfo);

	/**
	 * Notification event that a directory has been updated.
	 * @param directory the updated directory.
	 * @param projectInfo the project the owns the updated directory.
	 */
	public void directoryUpdated(IFileStore directory, ProjectInfo projectInfo);

	/**
	 * Notification event that a file has been created.
	 * @param file the created file.
	 * @param projectInfo the project the owns the created file.
	 */
	public void fileCreated(IFileStore file, ProjectInfo projectInfo);

	/**
	 * Notification event that a file has been deleted.
	 * @param file the deleted file.
	 * @param projectInfo the project the owns the deleted file.
	 */
	public void fileDeleted(IFileStore file, ProjectInfo projectInfo);

	/**
	 * Notification event that a file has been updated.
	 * @param file the updated file.
	 * @param projectInfo the project the owns the updated file.
	 */
	public void fileUpdated(IFileStore file, ProjectInfo projectInfo);

}
