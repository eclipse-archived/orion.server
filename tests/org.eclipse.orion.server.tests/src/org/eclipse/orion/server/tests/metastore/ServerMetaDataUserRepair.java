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
package org.eclipse.orion.server.tests.metastore;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.crypto.spec.PBEKeySpec;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.osgi.service.datalocation.Location;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * Temporary test class that I used to recover the server metadata in the Users.pref file.
 * 
 * @author Anthony Hunter
 */
public class ServerMetaDataUserRepair {

	private ISecurePreferences storage;
	String USERS_FILE = "/workspace/junit-workspace/.metadata/.plugins/org.eclipse.orion.server.core/.settings/Users.prefs";
	static final String ORION_SERVER_NODE = "org.eclipse.orion.server"; //$NON-NLS-1$
	static final String USERS = "users"; //$NON-NLS-1$

	private PrintStream migrationLog;

	@Test
	public void repairUserMetadata() throws StorageException {
		initSecurePreferences();
		ISecurePreferences usersPrefs = storage.node(USERS);
		int userCount = 0;
		int foundCount = 0;
		migrationLogOpen();
		Map<String, Map<String, String>> users = getMetadataFromFile(USERS_FILE);
		for (String uid : usersPrefs.childrenNames()) {
			ISecurePreferences userPrefs = usersPrefs.node(uid);
			String login = userPrefs.get("login", "");
			String name = userPrefs.get("name", "");

			if (users.containsKey(uid)) {
				foundCount++;
			} else {
				String prefName = uid + "/Name=" + login + "\n";
				String prefUserName = uid + "/UserName=" + name + "\n";
				String prefUserRights = uid + "/UserRights=[{\"Method\"\\:15,\"Uri\"\\:\"/users/" + uid + "\"},{\"Method\"\\:15,\"Uri\"\\:\"/workspace/" + login + "\"},{\"Method\"\\:15,\"Uri\"\\:\"/workspace/" + login + "/*\"},{\"Method\"\\:15,\"Uri\"\\:\"/file/" + login + "\"},{\"Method\"\\:15,\"Uri\"\\:\"/file/" + login + "/*\"}]\n";
				String prefUserRightsVersion = uid + "/UserRightsVersion=3\n";
				String prefWorkspaces = uid + "/Workspaces=[{\"Id\"\\:\"" + login + "\",\"LastModified\"\\:1380546078642}]\"\n";
				addLine(USERS_FILE, prefName, prefUserName, prefUserRights, prefUserRightsVersion, prefWorkspaces);
				migrationLogPrint("Added missing uid " + uid + ".");
			}
			userCount++;

		}
		migrationLogPrint("There are " + userCount + " users.");
		migrationLogPrint("There are " + foundCount + " users found.");
		migrationLogClose();
	}

	private void initSecurePreferences() {
		//try to create our own secure storage under the platform instance location
		URL location = getStorageLocation();
		if (location != null) {
			Map<String, Object> options = new HashMap<String, Object>();
			options.put(IProviderHints.PROMPT_USER, Boolean.FALSE);
			String password = System.getProperty(Activator.ORION_STORAGE_PASSWORD, ""); //$NON-NLS-1$
			options.put(IProviderHints.DEFAULT_PASSWORD, new PBEKeySpec(password.toCharArray()));
			try {
				storage = SecurePreferencesFactory.open(location, options);
			} catch (IOException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_USER_SECURESTORAGE, "Error initializing user storage location", e)); //$NON-NLS-1$
			}
		} else {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_USER_SECURESTORAGE, "No instance location set. Storing user data in user home directory")); //$NON-NLS-1$
		}
		//fall back to default secure storage location if we failed to create our own
		if (storage == null)
			storage = SecurePreferencesFactory.getDefault().node(ORION_SERVER_NODE);
	}

	/**
	 * Returns the location for user data to be stored.
	 */
	private URL getStorageLocation() {
		BundleContext context = Activator.getContext();
		Collection<ServiceReference<Location>> refs;
		try {
			refs = context.getServiceReferences(Location.class, Location.INSTANCE_FILTER);
		} catch (InvalidSyntaxException e) {
			// we know the instance location filter syntax is valid
			throw new RuntimeException(e);
		}
		if (refs.isEmpty())
			return null;
		ServiceReference<Location> ref = refs.iterator().next();
		Location location = context.getService(ref);
		try {
			try {
				if (location != null)
					return location.getDataArea(Activator.PI_USER_SECURESTORAGE + "/user_store"); //$NON-NLS-1$
			} catch (IOException e) {
				LogHelper.log(e);
			}
		} finally {
			context.ungetService(ref);
		}
		//return null if we are unable to determine instance location.
		return null;
	}

	/**
	 * Get a metadata list from the provided file containing properties. The metadata is stored in a Map
	 * with the key being the id and the value being a list of properties.
	 * @param file file containing properties.
	 * @return metadata list.
	 */
	private Map<String, Map<String, String>> getMetadataFromFile(String file) {
		Map<String, Map<String, String>> metaData = new HashMap<String, Map<String, String>>();
		Properties properties = getPropertiesFromFile(file);
		for (Object key : properties.keySet()) {
			String keyString = (String) key;
			if (keyString.equals("eclipse.preferences.version")) {
				continue;
			}
			String uniqueId = keyString.substring(0, keyString.indexOf("/"));
			String propertyKey = keyString.substring(keyString.indexOf("/") + 1);
			String propertyValue = properties.getProperty(keyString);
			if (metaData.containsKey(uniqueId)) {
				Map<String, String> propertyMap = metaData.get(uniqueId);
				propertyMap.put(propertyKey, propertyValue);
			} else {
				Map<String, String> propertyMap = new HashMap<String, String>();
				propertyMap.put(propertyKey, propertyValue);
				metaData.put(uniqueId, propertyMap);
			}
		}
		return metaData;
	}

	/**
	 * Get all the properties from the provided file containing properties.
	 * @param file file containing properties.
	 * @return properties list.
	 */
	private Properties getPropertiesFromFile(String file) {
		Properties properties = new Properties();
		BufferedInputStream inStream;
		try {
			inStream = new BufferedInputStream(new FileInputStream(new File(file)));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		try {
			properties.load(inStream);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				inStream.close();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
		return properties;
	}

	private void addLine(String file, String a, String b, String c, String d, String e) {
		try {
			Writer output = new BufferedWriter(new FileWriter(file, true));
			output.append(a);
			output.append(b);
			output.append(c);
			output.append(d);
			output.append(e);
			output.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Close the migration log.
	 */
	private void migrationLogClose() {
		migrationLog.close();
	}

	/** 
	 * Open the migration log, the log is stored at the user provided workspace root.
	 * @param workspaceRoot the workspace root.
	 * @throws FileNotFoundException
	 */
	private void migrationLogOpen() {
		try {
			File logFile = new File("migration.log");
			FileOutputStream stream = new FileOutputStream(logFile);
			System.err.println("ProjectMigration: migration log is at " + logFile.getAbsolutePath());
			migrationLog = new PrintStream(stream);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Print a message to the migration log.
	 * @param message the message.
	 */
	private void migrationLogPrint(String message) {
		migrationLog.println(message);
	}

}
