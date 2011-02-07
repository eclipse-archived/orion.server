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

import com.jcraft.jsch.*;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.server.filesystem.git.KeysCredentials;

/**
 * Loads known hosts and keys for a Orion user.
 * <p>
 * In contrast with its base class this implementation ignores any OpenSSH
 * configuration.
 * <p>
 * The implementation assumes to work in a batch mode. If user interactivity is
 * required by SSH (e.g. to obtain a password), the connection will fail.
 */
public class OrionSshSessionFactory extends SshConfigSessionFactory {

	private static final int SSH_PORT = 22;

	@Override
	public synchronized Session getSession(String user, String pass,
			String host, int port, CredentialsProvider credentialsProvider,
			FS fs) throws JSchException {
		if (user == null && credentialsProvider instanceof OrionUserCredentialsProvider) {
			OrionUserCredentialsProvider oucp = (OrionUserCredentialsProvider) credentialsProvider;
			CredentialsProvider cp = SshConfigManager.getDefault().getCredentialsProvider(oucp.getOrionUser(), oucp.getUri());
			if (cp != null) {
				CredentialItem.Username u = new CredentialItem.Username();
				if (cp.supports(u) && cp.get(oucp.getUri(), u)) {
					user = u.getValue();
				}
			}
		}
		if (port <= 0)
			port = SSH_PORT;

		final Session session = createSession(user, host, port, credentialsProvider);
		if (pass != null)
			session.setPassword(pass);
		if (credentialsProvider != null
				&& !credentialsProvider.isInteractive()) {
			session.setUserInfo(new CredentialsProviderUserInfo(session,
					credentialsProvider));
		}
		return session;
	}

	protected Session createSession(final String user, final String host, final int port, CredentialsProvider cp)
			throws JSchException {
		return getJSch(cp).getSession(user, host, port);
	}

	protected JSch getJSch(final CredentialsProvider cp) throws JSchException {
		return createDefaultJSch(cp);
	}

	protected JSch createDefaultJSch(CredentialsProvider cp) throws JSchException {
		final JSch jsch = new JSch();
		if (cp instanceof OrionUserCredentialsProvider) {
			OrionUserCredentialsProvider oucp = (OrionUserCredentialsProvider) cp;
			knownHosts(jsch, oucp.getOrionUser());
			identities(jsch, oucp.getOrionUser(), oucp.getUri());
		}
		return jsch;
	}

	private static void knownHosts(final JSch sch, String orionUser) throws JSchException {
		try {
			InputStream in = null;
			try {
				in = SshConfigManager.getDefault().getKnownHosts(orionUser);
				sch.setKnownHosts(in);
			} finally {
				in.close();
			}
		} catch (IOException err) {
			// should never happen
		}
	}

	private static void identities(final JSch sch, String orionUser, URIish uri) {
		KeysCredentials[] keysCredentials = SshConfigManager.getDefault().getKeysCredentials(orionUser, uri);
		for (KeysCredentials kc : keysCredentials) {
			String name = kc.getName();
			byte[] privateBytes = kc.getPrivateKey().getBytes();
			byte[] publicBytes = kc.getPublicKey().getBytes();
			byte[] passhraseBytes = kc.getPublicKey().getBytes();
			try {
				sch.addIdentity(name, privateBytes, publicBytes, passhraseBytes);
			} catch (JSchException e) {
				// pretend the key doesn't exist
			}
		}
	}

	@Override
	protected void configure(Host hc, Session session) {
		// nothing to do
	}

}
