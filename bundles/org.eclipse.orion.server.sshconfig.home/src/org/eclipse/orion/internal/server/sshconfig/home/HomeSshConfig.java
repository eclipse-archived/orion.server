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
package org.eclipse.orion.internal.server.sshconfig.home;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.server.filesystem.git.KeysCredentials;
import org.eclipse.orion.server.filesystem.git.ISshConfig;

public class HomeSshConfig implements ISshConfig {

	@Override
	public String[] getKnownHosts(String orionUser) {
		String userHome = userHome();
		final File home = new File(userHome);
		if (!home.exists())
			return new String[0];
		final File known_hosts = new File(new File(home, ".ssh"), "known_hosts");
		List<String> knownHosts = new ArrayList<String>();
		try {
			final FileInputStream in = new FileInputStream(known_hosts);
			try {
				BufferedReader bufferedReader = new BufferedReader(
						new InputStreamReader(in));
				String line;
				while ((line = bufferedReader.readLine()) != null) {
					knownHosts.add(line);
				}
			} finally {
				in.close();
			}
		} catch (FileNotFoundException none) {
			// no known hosts in home
		} catch (IOException err) {
			// no known hosts in home
		}
		return knownHosts.toArray(new String[0]);
	}

	@Override
	public CredentialsProvider getCredentialsProvider(String orionUser, URIish uri) {
		return new UsernameCredentialsProvider(userName());
	}

	@Override
	public KeysCredentials[] getKeysCredentials(String orionUser, URIish uri) {
		String userHome = userHome();
		final File home = new File(userHome);
		if (!home.exists())
			return new KeysCredentials[0];
		final File sshdir = new File(home, ".ssh");
		List<KeysCredentials> keysCredentials = new ArrayList<KeysCredentials>();
		if (sshdir.isDirectory()) {
			String[] names = new String[] {"identity", "id_rsa", "id_dsa"};
			for (String name : names) {
				KeysCredentials kc = loadKeysCredentials(new File(sshdir, name));
				if (kc != null)
					keysCredentials.add(kc);
			}
		}
		return keysCredentials.toArray(new KeysCredentials[0]);
	}

	private KeysCredentials loadKeysCredentials(File file) {
		if (!file.isFile())
			return null;
		/*
		 * No passphare in home dir. If the private key is protected this config
		 * will fail. #getCredentialsProvider returns null so it won't help with
		 * the passphrase either.
		 */
		String passphrase = null;

		try {
			final FileInputStream priv = new FileInputStream(file);
			String privateKey = toString(priv);
			final FileInputStream pub = new FileInputStream(file + ".pub");
			String publicKey = toString(pub);

			return new KeysCredentials(file.getName(), publicKey, privateKey, passphrase);
		} catch (FileNotFoundException e) {
			// missing key file
		}
		return null;
	}

	private String toString(FileInputStream in ) {
		int ch;
		StringBuffer sb = new StringBuffer("");
		try {
			while ((ch = in.read()) != -1)
				sb.append((char) ch);
			in.close();
		} catch (IOException e) {
			// ignore and return what you've got so far, even an empty String
		}
		return sb.toString();
	}

	static String userHome() {
		return AccessController.doPrivileged(new PrivilegedAction<String>() {
			public String run() {
				return System.getProperty("user.home");
			}
		});
	}

	static String userName() {
		return AccessController.doPrivileged(new PrivilegedAction<String>() {
			public String run() {
				return System.getProperty("user.name");
			}
		});
	}
}
