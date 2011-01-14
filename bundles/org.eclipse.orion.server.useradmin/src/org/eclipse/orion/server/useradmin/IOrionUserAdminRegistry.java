/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin;

import java.util.Set;

/**
 * Stores available {@link OrionUserAdmin}s used for authentication.
 * If a new {@link OrionUserAdmin} service is deployed it is automatically
 * registered and may be used for authentication.
 *
 */
public interface IOrionUserAdminRegistry {

	/**
	 * If an {@link OrionUserAdmin} of this name exists it will be returned as default by {@link #getUserStore()}
	 */
	public static String eclipseWebUsrAdminName = "EclipseWeb";

	/**
	 * Lists User stores supported by this instance of Eclipse Web.
	 * <br>Result may be used to obtain {@link OrionUserAdmin} by {@link #getUserStore(String)}.
	 * @return
	 */
	public Set<String> getSupportedUserStores();

	/**
	 * Returns {@link OrionUserAdmin} handling store of a given name
	 * @param storeName
	 * @return
	 * @throws UnsupportedUserStoreException if there is no {@link OrionUserAdmin} service deployed with given name
	 * 
	 * @see OrionUserAdmin#getStoreName()
	 */
	public OrionUserAdmin getUserStore(String storeName) throws UnsupportedUserStoreException;

	/**
	 * Returns {@link OrionUserAdmin} handling default store.<br> 
	 * If there is only one {@link OrionUserAdmin} service available it will be this service.
	 * If there are more than one {@link OrionUserAdmin} services available the first to be 
	 * registered is returned or the service having {@link #eclipseWebUsrAdminName} name.  
	 * @return
	 */
	public OrionUserAdmin getUserStore();

}
