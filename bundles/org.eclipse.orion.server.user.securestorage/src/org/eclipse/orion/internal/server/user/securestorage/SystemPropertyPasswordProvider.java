/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.user.securestorage;

import javax.crypto.spec.PBEKeySpec;
import org.eclipse.equinox.security.storage.provider.IPreferencesContainer;

import org.eclipse.equinox.security.storage.provider.PasswordProvider;

/**
 * A default password provider that obtains a password from a system property.
 */
public class SystemPropertyPasswordProvider extends PasswordProvider {

	@Override
	public PBEKeySpec getPassword(IPreferencesContainer container, int passwordType) {
		String prop = System.getProperty(Activator.ORION_STORAGE_PASSWORD, ""); //$NON-NLS-1$
		return new PBEKeySpec(prop.toCharArray());
	}

}
