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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * Exception thrown when requested {@link IOrionCredentialsService} is not registered.
 *
 */
public class UnsupportedUserStoreException extends CoreException {

	public UnsupportedUserStoreException() {
		super(new Status(IStatus.ERROR, UserAdminActivator.PI_USERADMIN, "Given user store is not found."));
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -269798742696785135L;

}
