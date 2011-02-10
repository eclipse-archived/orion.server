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
package org.eclipse.orion.internal.server.sshconfig.userprofile;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.orion.server.filesystem.git.ISshConfig;
import org.eclipse.orion.server.filesystem.git.KeysCredentials;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;

public class UserProfileSshConfig implements ISshConfig {

	static final String ORION_SERVER_NODE = "org.eclipse.orion.server"; //$NON-NLS-1$

	/**
	 * The system property name for the secure storage master password.
	 */
	public static final String ORION_STORAGE_PASSWORD = "orion.storage.password"; //$NON-NLS-1$

	// SSH_CONFIG (node)
	// |_KNOWN_HOSTS (key)
	// |_<uri1> (node)
	// | |_USERNAME (key)
	// | |_PASSWORD* (key)
	// | |_KEYS* (node)
	// |   |_<name1>* (node)
	// |   | |_PUBLIC_KEY (key)
	// |   | |_PRIVATE_KEY (key)
	// |   | |_PASSPHRASE* (key)
	// |   |_<name2>* (node)
	// |     |_...
	// |_<uri2>* (node)
	//   |_...

	// * optional nodes
	// <uri>s are encoded with java.net.URLEncoder


	private static final String SSH_CONFIG = Activator.PI_SSHCONFIG_USERPROFILE;
	private static final String KNOWN_HOSTS = "knownHosts"; //$NON-NLS-1$
	private static final String KEYS = "keys"; //$NON-NLS-1$
	private static final String PUBLIC_KEY = "publicKey"; //$NON-NLS-1$
	private static final String PRIVATE_KEY = "privateKey"; //$NON-NLS-1$
	private static final String PASSPHRASE = "passphrase"; //$NON-NLS-1$
	private static final String USERNAME = "username"; //$NON-NLS-1$
	private static final String PASSWORD = "password"; //$NON-NLS-1$

	private IOrionUserProfileService userProfileService;

	public UserProfileSshConfig() {
	}

	public String[] getKnownHosts(String orionUser) {
		try {
			// split by "\r\n" ?
			return userProfileService.getUserProfileNode(orionUser, SSH_CONFIG).get(KNOWN_HOSTS, "").split("\n");
		} catch (CoreException e) {
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
		if (!userProfileService.getUserProfileNode(orionUser, SSH_CONFIG).userProfileNodeExists(encodedUri)) {
			return null;
		}
		IOrionUserProfileNode uriNode = userProfileService.getUserProfileNode(orionUser, SSH_CONFIG).getUserProfileNode(encodedUri);
		try {
			String username = uriNode.get(USERNAME, null);
			String password = uriNode.get(PASSWORD, "");
			return new UsernamePasswordCredentialsProvider(username, password);
		} catch (CoreException e) {
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
		if (!userProfileService.getUserProfileNode(orionUser, SSH_CONFIG).getUserProfileNode(encodedUri).userProfileNodeExists(KEYS)) {
			return new KeysCredentials[0];
		}
		List<KeysCredentials> result = new ArrayList<KeysCredentials>();
		IOrionUserProfileNode keysNode = userProfileService.getUserProfileNode(orionUser, SSH_CONFIG).getUserProfileNode(encodedUri).getUserProfileNode(KEYS);
		for (String name : keysNode.childrenNames()) {
			try {
				String publicKey = keysNode.getUserProfileNode(name).get(PUBLIC_KEY, null);
				String privateKey = keysNode.getUserProfileNode(name).get(PRIVATE_KEY, null);
				String passphrase = keysNode.getUserProfileNode(name).get(PASSPHRASE, null);
				result.add(new KeysCredentials(name, publicKey, privateKey, passphrase));
			} catch (CoreException e) {
				// ignore and continue with next key set
			}
		}
		return result.toArray(new KeysCredentials[0]);
	}

	public void bindUserProfileService(IOrionUserProfileService userProfileService) {
		this.userProfileService = userProfileService;
	}

	public void unbindUserProfileService(IOrionUserProfileService userProfileService) {
		this.userProfileService = null;
	}

}
