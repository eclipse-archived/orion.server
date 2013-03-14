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
package org.eclipse.orion.server.core;

import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.orion.internal.server.core.Activator;

/**
 * A helper class to facilitate accessing orion server configuration preferences.
 * This class will traverse preferences according to the scope order defined
 * by the Equinox preference service. This typically means configuration preferences
 * are consulted, followed by instance preferences.
 */
public class PreferenceHelper {
	/**
	 * Returns the value of the string preference corresponding to the given key,
	 * or <code>null</code> if undefined.
	 */
	public static String getString(String key) {
		return getString(key, null);
	}

	/**
	 * Returns the value of the string preference corresponding to the given key,
	 * or the provided default value if not defined.
	 */
	public static String getString(String key, String defaultValue) {
		IPreferencesService service = Activator.getPreferenceService();
		if (service == null)
			return null;
		return service.getString(ServerConstants.PREFERENCE_SCOPE, key, defaultValue, null);
	}

	/**
	 * Returns the value of the integer preference corresponding to the given key,
	 * or the provided default value if not defined.
	 */
	public static int getInt(String key, int defaultValue) {
		IPreferencesService service = Activator.getPreferenceService();
		if (service == null)
			return defaultValue;
		return service.getInt(ServerConstants.PREFERENCE_SCOPE, key, defaultValue, null);
	}

}
