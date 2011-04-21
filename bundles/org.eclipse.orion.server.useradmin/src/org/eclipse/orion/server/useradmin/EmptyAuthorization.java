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

import org.osgi.service.useradmin.Authorization;

/**
 * Used when users is not authorized.
 * 
 */
public class EmptyAuthorization implements Authorization {

	public String getName() {
		return "";
	}

	public boolean hasRole(String name) {
		return false;
	}

	public String[] getRoles() {
		return new String[0];
	}

}
