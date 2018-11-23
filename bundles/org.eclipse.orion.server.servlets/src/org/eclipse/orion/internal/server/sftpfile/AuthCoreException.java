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
package org.eclipse.orion.internal.server.sftpfile;

import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.osgi.util.NLS;

/**
 * A {@link CoreException} indicating an authentication failure.
 */
public class AuthCoreException extends CoreException {
	/**
	 * Default serial version Id.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The authentication realm where the failure occurred.
	 */
	private String realm;

	/**
	 * Creates a new authentication exception on the given realm.
	 */
	public AuthCoreException(String realm) {
		//TODO replace 280 with EFS.ERROR_AUTH_FAILED when available
		super(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, 280, NLS.bind("Failed to authenticate to host: {0}", realm), null));
		this.realm = realm;
	}

	/**
	 * Returns the realm where the authentication is required.
	 */
	public String getRealm() {
		return realm;
	}

}
