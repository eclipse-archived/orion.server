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

/**
 * Interface for the Orion file change notification service. It has allows server side
 * processes to be notified when there are change events within the Orion server workspace.
 * There are separate events for create, update and delete events for both files and directories.
 *  
 * @author Anthony Hunter
 */
public interface IFileChangeNotificationService {
	
	/**
	 * Register the provided listener with the file change notification service.
	 * @param listener
	 */
	public void addListener(IFileChangeListener listener);

	/**
	 * Unregister the provided listener from the file change notification service.
	 * @param listener
	 */
	public void removeListener(IFileChangeListener listener);

}
