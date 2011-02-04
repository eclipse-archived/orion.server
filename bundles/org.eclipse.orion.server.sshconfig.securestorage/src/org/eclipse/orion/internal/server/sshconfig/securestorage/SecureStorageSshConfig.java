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
package org.eclipse.orion.internal.server.sshconfig.securestorage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.PBEKeySpec;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.equinox.security.storage.provider.IProviderHints;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.filesystem.git.ISshConfig;
import org.eclipse.orion.server.filesystem.git.KeysCredentials;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

public class SecureStorageSshConfig implements ISshConfig {

	static final String ORION_SERVER_NODE = "org.eclipse.orion.server"; //$NON-NLS-1$

	/**
	 * The system property name for the secure storage master password.
	 */
	public static final String ORION_STORAGE_PASSWORD = "orion.storage.password"; //$NON-NLS-1$

	// SSH_CONFIG
	// |_<orionUser>
	//   |_KNOWN_HOSTS
	//   |_<uri1>
	//   | |_USERNAME
	//   | |_PASSWORD*
	//   | |_KEYS*
	//   |   |_<name1>*
	//   |   | |_PUBLIC_KEY
	//   |   | |_PRIVATE_KEY
	//   |   | |_PASSPHRASE*
	//   |   |_<name2>*
	//   |     |_...
	//   |_<uri2>*
	//     |_...

	// * optional nodes
	// <uri>s are encoded with java.net.URLEncoder


	private static final String SSH_CONFIG = Activator.PI_SSHCONFIG_SECURESTORAGE;
	private static final String KNOWN_HOSTS = "knownHosts"; //$NON-NLS-1$
	private static final String KEYS = "keys"; //$NON-NLS-1$
	private static final String PUBLIC_KEY = "publicKey"; //$NON-NLS-1$
	private static final String PRIVATE_KEY = "privateKey"; //$NON-NLS-1$
	private static final String PASSPHRASE = "passphrase"; //$NON-NLS-1$
	private static final String USERNAME = "username"; //$NON-NLS-1$
	private static final String PASSWORD = "password"; //$NON-NLS-1$

	private ISecurePreferences storage;

	public SecureStorageSshConfig() {
		initSecurePreferences();
	}

	public String[] getKnownHosts(String orionUser) {
		if (!storage.node(SSH_CONFIG).nodeExists(orionUser)) {
			return new String[0];
		}
		try {
			// split by "\r\n" ?
			return storage.node(SSH_CONFIG).node(orionUser).get(KNOWN_HOSTS, "").split("\n");
		} catch (StorageException e) {
			// ignore and return an empty array
		}
		return new String[0];
	}

	public CredentialsProvider getCredentialsProvider(String orionUser, URIish uri) {
		String encodedUri;
		try {
			encodedUri = URLEncoder.encode(uri.toString(), "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			return null;
		}
		if (!storage.node(SSH_CONFIG).node(orionUser).nodeExists(encodedUri)) {
			return null;
		}
		ISecurePreferences uriNode = storage.node(SSH_CONFIG).node(orionUser).node(encodedUri);
		try {
			String username = uriNode.get(USERNAME, null);
			String password = uriNode.get(PASSWORD, null);
			return new UsernamePasswordCredentialsProvider(username, password);
		} catch (StorageException e) {
			// ignore and return null
		}
		return null;
	}

	public KeysCredentials[] getKeysCredentials(String orionUser, URIish uri) {
		String encodedUri;
		try {
			encodedUri = URLEncoder.encode(uri.toString(), "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			return new KeysCredentials[0];
		}
		if (!storage.node(SSH_CONFIG).node(orionUser).node(encodedUri).nodeExists(KEYS)) {
			return new KeysCredentials[0];
		}
		List<KeysCredentials> result = new ArrayList<KeysCredentials>();
		ISecurePreferences keysNode = storage.node(SSH_CONFIG).node(orionUser).node(encodedUri).node(KEYS);
		for (String name : keysNode.childrenNames()) {
			try {
				String publicKey = keysNode.node(name).get(PUBLIC_KEY, null);
				String privateKey = keysNode.node(name).get(PRIVATE_KEY, null);
				String passphrase = keysNode.node(name).get(PASSPHRASE, null);
				result.add(new KeysCredentials(name, publicKey, privateKey, passphrase));
			} catch (StorageException e) {
				// ignore and continue with next key set
			}
		}
		return result.toArray(new KeysCredentials[0]);
	}

	private void initSecurePreferences() {
		//try to create our own secure storage under the platform instance location
		URL location = getStorageLocation();
		if (location != null) {
			Map<String, Object> options = new HashMap<String, Object>();
			options.put(IProviderHints.PROMPT_USER, Boolean.FALSE);
			String password = System.getProperty(ORION_STORAGE_PASSWORD, ""); //$NON-NLS-1$
			options.put(IProviderHints.DEFAULT_PASSWORD, new PBEKeySpec(password.toCharArray()));
			try {
				storage = SecurePreferencesFactory.open(location, options);
			} catch (IOException e) {
				LogHelper.log(new Status(IStatus.ERROR, Activator.PI_SSHCONFIG_SECURESTORAGE, "Error initializing user storage location", e)); //$NON-NLS-1$
			}
		} else {
			LogHelper.log(new Status(IStatus.WARNING, Activator.PI_SSHCONFIG_SECURESTORAGE, "No instance location set. Storing user data in user home directory")); //$NON-NLS-1$
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
					return location.getDataArea(Activator.PI_SSHCONFIG_SECURESTORAGE + "/user_store"); //$NON-NLS-1$
			} catch (IOException e) {
				LogHelper.log(e);
			}
		} finally {
			context.ungetService(ref);
		}
		//return null if we are unable to determine instance location.
		return null;
	}

}
