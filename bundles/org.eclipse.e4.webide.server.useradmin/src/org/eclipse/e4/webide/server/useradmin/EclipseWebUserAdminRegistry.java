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
package org.eclipse.e4.webide.server.useradmin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.osgi.service.useradmin.UserAdmin;

public class EclipseWebUserAdminRegistry implements IEclipseWebUserAdminRegistry {

	private Map<String, EclipseWebUserAdmin> userStores = new HashMap<String, EclipseWebUserAdmin>();
	private EclipseWebUserAdmin defaultUserAdmin;
	private static IEclipseWebUserAdminRegistry singleton;

	public static IEclipseWebUserAdminRegistry getDefault() {
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
		if (userAdmin instanceof EclipseWebUserAdmin) {
			EclipseWebUserAdmin eclipseWebUserAdmin = (EclipseWebUserAdmin) userAdmin;
			userStores.put(eclipseWebUserAdmin.getStoreName(), eclipseWebUserAdmin);
			if (defaultUserAdmin == null || eclipseWebUsrAdminName.equals(eclipseWebUserAdmin.getStoreName())) {
				defaultUserAdmin = eclipseWebUserAdmin;
			}
		}
	}

	public void unsetUserAdmin(UserAdmin userAdmin) {
		if (userAdmin instanceof EclipseWebUserAdmin) {
			EclipseWebUserAdmin eclipseWebUserAdmin = (EclipseWebUserAdmin) userAdmin;
			userStores.remove(eclipseWebUserAdmin.getStoreName());
			if (userAdmin.equals(defaultUserAdmin)) {
				defaultUserAdmin = userStores.values().iterator().next();
			}
		}

	}

	@Override
	public EclipseWebUserAdmin getUserStore(String storeName) {
		return userStores.get(storeName);
	}

	@Override
	public EclipseWebUserAdmin getUserStore() {
		return defaultUserAdmin;
	}

}
