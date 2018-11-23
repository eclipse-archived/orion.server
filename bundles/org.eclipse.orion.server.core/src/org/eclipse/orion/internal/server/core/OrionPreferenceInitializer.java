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
package org.eclipse.orion.internal.server.core;

import java.io.File;
import java.net.URL;
import org.eclipse.orion.server.core.IOUtilities;
import java.io.*;
import java.util.Properties;
import org.eclipse.core.runtime.preferences.*;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes default value for server configuration preferences.
 */
public class OrionPreferenceInitializer extends AbstractPreferenceInitializer {
	private static final String DEFAULT_CONFIG_FILE = "orion.conf"; //$NON-NLS-1$

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$

	/**
	 * Locate and return the server configuration file. Returns null if the file could not be found.
	 */
	public File findServerConfigFile() {
		String location = Activator.getDefault().getProperty(ServerConstants.PROP_CONFIG_FILE_LOCATION);
		if (location == null)
			location = DEFAULT_CONFIG_FILE;

		//try the working directory
		File result = new File(location);
		if (result.exists())
			return result;
		logger.info("No server configuration file found at: " + result.getAbsolutePath()); //$NON-NLS-1$

		//try the platform instance location
		URL instanceURL = Activator.getDefault().getInstanceLocation().getURL();
		// strip off file: prefix from URL
		result = new File(new File(instanceURL.toExternalForm().substring(5)), DEFAULT_CONFIG_FILE);
		if (result.exists())
			return result;
		logger.info("No server configuration file found at: " + result); //$NON-NLS-1$
		return null;
	}

	@Override
	public void initializeDefaultPreferences() {
		File configFile = findServerConfigFile();
		if (configFile == null)
			return;
		Properties props = readProperties(configFile);
		if (props == null)
			return;
		//load configuration preferences into the default scope
		IEclipsePreferences node = DefaultScope.INSTANCE.getNode(ServerConstants.PREFERENCE_SCOPE);
		for (Object o : props.keySet()) {
			String key = (String) o;
			node.put(key, props.getProperty(key));
		}
	}

	/**
	 * Returns a property object populated with the contents of the given property
	 * file. Returns <code>null</code> if there was any error reading the properties.
	 */
	private Properties readProperties(File propFile) {
		Properties props = new Properties();
		try {
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(propFile));
			try {
				props.load(in);
			} finally {
				IOUtilities.safeClose(in);
			}
		} catch (IOException e) {
			logger.warn("Unable to read server configuration file at: " + propFile); //$NON-NLS-1$
			LogHelper.log(e);
		}
		logger.info("Server configuration file loaded from: " + propFile); //$NON-NLS-1$
		return props;
	}
}
