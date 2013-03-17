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

import org.eclipse.orion.server.user.profile.IOrionUserProfileService;

public class UserServiceHelper {

	private Map<String, IOrionCredentialsService> userStores = new HashMap<String, IOrionCredentialsService>();
	private IOrionCredentialsService defaultUserAdmin;
	private IOrionGuestCredentialsService guestUserAdmin;

	private IOrionUserProfileService userProfileService;

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

	public void setUserAdmin(IOrionCredentialsService userAdmin) {
		if (userAdmin instanceof IOrionCredentialsService) {
			IOrionCredentialsService eclipseWebUserAdmin = (IOrionCredentialsService) userAdmin;
			userStores.put(eclipseWebUserAdmin.getStoreName(), eclipseWebUserAdmin);
			if (defaultUserAdmin == null || UserAdminActivator.eclipseWebUsrAdminName.equals(eclipseWebUserAdmin.getStoreName())) {
				defaultUserAdmin = eclipseWebUserAdmin;
			}
		}
	}

	public void unsetUserAdmin(IOrionCredentialsService userAdmin) {
		if (userAdmin instanceof IOrionCredentialsService) {
			IOrionCredentialsService eclipseWebUserAdmin = (IOrionCredentialsService) userAdmin;
			userStores.remove(eclipseWebUserAdmin.getStoreName());
			if (userAdmin.equals(defaultUserAdmin)) {
				Iterator<IOrionCredentialsService> iterator = userStores.values().iterator();
				if (iterator.hasNext())
					defaultUserAdmin = iterator.next();
			}
		}

	}

	public IOrionCredentialsService getUserStore() {
		return defaultUserAdmin;
	}

	public IOrionUserProfileService getUserProfileService() {
		return userProfileService;
	}

	public void bindUserProfileService(IOrionUserProfileService userProfileService) {
		this.userProfileService = userProfileService;
	}

	public void unbindUserProfileService(IOrionUserProfileService userProfileService) {
		this.userProfileService = null;
	}

	public IOrionCredentialsService getGuestUserStore() {
		return guestUserAdmin;
	}

	public void setGuestUserAdmin(IOrionGuestCredentialsService guestUserAdmin) {
		this.guestUserAdmin = guestUserAdmin;
	}

	public void unsetGuestUserAdmin(IOrionGuestCredentialsService guestUserAdmin) {
		if (guestUserAdmin.equals(this.guestUserAdmin)) {
			this.guestUserAdmin = null;
		}
	}
}
