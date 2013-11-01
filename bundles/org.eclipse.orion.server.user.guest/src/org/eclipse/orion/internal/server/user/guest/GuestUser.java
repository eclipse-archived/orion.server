/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.user.guest;

import org.eclipse.orion.server.useradmin.User;
import org.eclipse.orion.server.useradmin.UserConstants;

/**
 * Guest user, distinguished from a normal user by having the UserConstants.KEY_GUEST property
 */
public class GuestUser extends User {
	public GuestUser(String uid, String login, String name, String password) {
		super(uid, login, name, password);
		this.addProperty(UserConstants.KEY_GUEST, Boolean.TRUE.toString());
	}
}

