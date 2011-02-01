/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.filesystem.git;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.server.filesystem.git.KeysCredentials;
import org.eclipse.orion.server.filesystem.git.ISshConfig;

public class SshConfigManager {

	private List<ISshConfig> sshConfigs = Collections.synchronizedList(new ArrayList<ISshConfig>());

	private static SshConfigManager singleton;

	public static SshConfigManager getDefault() {
		return singleton;
	}

	public void activate() {
		singleton = this;
	}

	public void deactivate() {
		singleton = null;
	}

	public void addSshConfig(ISshConfig sshConfig) {
		sshConfigs.add(sshConfig);
	}

	public void removeSshConfig(ISshConfig sshConfig) {
		sshConfigs.remove(sshConfig);
	}

	public CredentialsProvider getCredentialsProvider(String orionUser, URIish uri) {
		for (ISshConfig config : sshConfigs) {
			CredentialsProvider cp = config.getCredentialsProvider(orionUser, uri);
			if (cp != null)
				return cp;
		}
		return null;
	}

	public InputStream getKnownHosts(String orionUser) {
		List<String> allKnownHosts = new ArrayList<String>();
		for (ISshConfig config : sshConfigs) {
			String[] knownHosts = config.getKnownHosts(orionUser);
			for (String kh : knownHosts) {
				allKnownHosts.add(kh);
			}
		}
		StringBuffer sb = new StringBuffer();
		for (String kh : allKnownHosts) {
			sb.append(kh);
			sb.append("\n");
		}
		return new ByteArrayInputStream(sb.toString().getBytes());
	}

	public KeysCredentials[] getKeysCredentials(String orionUser, URIish uri) {
		List<KeysCredentials> allKeysCredentials = new ArrayList<KeysCredentials>();
		for (ISshConfig config : sshConfigs) {
			KeysCredentials[] keysCredentials = config.getKeysCredentials(orionUser, uri);
			for (KeysCredentials kc : keysCredentials) {
				allKeysCredentials.add(kc);
			}
		}
		return allKeysCredentials.toArray(new KeysCredentials[0]);
	}
}
