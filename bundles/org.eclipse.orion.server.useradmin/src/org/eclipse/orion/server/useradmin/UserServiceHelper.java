/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.useradmin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.osgi.service.useradmin.UserAdmin;

public class UserServiceHelper {

	private Map<String, OrionUserAdmin> userStores = new HashMap<String, OrionUserAdmin>();
	private OrionUserAdmin defaultUserAdmin;
	private static UserServiceHelper singleton;

	public static UserServiceHelper getDefault() {
		return singleton;
	}

	public void activate() {
		singleton = this;
	}

	public void deactivate() {
		singleton = null;
	}

	/**
	 * returns name of UserAdmins registered.
	 */
	public Set<String> getSupportedUserStores() {
		return userStores.keySet();
	}

	public void setUserAdmin(UserAdmin userAdmin) {
		if (userAdmin instanceof OrionUserAdmin) {
			OrionUserAdmin eclipseWebUserAdmin = (OrionUserAdmin) userAdmin;
			userStores.put(eclipseWebUserAdmin.getStoreName(), eclipseWebUserAdmin);
			if (defaultUserAdmin == null || UserAdminActivator.eclipseWebUsrAdminName.equals(eclipseWebUserAdmin.getStoreName())) {
				defaultUserAdmin = eclipseWebUserAdmin;
			}
		}
	}

	public void unsetUserAdmin(UserAdmin userAdmin) {
		if (userAdmin instanceof OrionUserAdmin) {
			OrionUserAdmin eclipseWebUserAdmin = (OrionUserAdmin) userAdmin;
			userStores.remove(eclipseWebUserAdmin.getStoreName());
			if (userAdmin.equals(defaultUserAdmin)) {
				Iterator<OrionUserAdmin> iterator = userStores.values().iterator();
				if (iterator.hasNext())
					defaultUserAdmin = iterator.next();
			}
		}

	}

	public OrionUserAdmin getUserStore(String storeName) {
		return userStores.get(storeName);
	}

	public OrionUserAdmin getUserStore() {
		return defaultUserAdmin;
	}

}
