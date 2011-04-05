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
package org.eclipse.orion.server.git;

import com.jcraft.jsch.*;
import java.io.*;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;

public class GitSshSessionFactory extends SshSessionFactory {

	private static final int SSH_PORT = 22;

	@Override
	public RemoteSession getSession(URIish uri, CredentialsProvider credentialsProvider, FS fs, int tms) throws TransportException {
		int port = uri.getPort();
		String user = uri.getUser();
		String pass = uri.getPass();
		if (credentialsProvider instanceof GitCredentialsProvider) {
			if (port <= 0)
				port = SSH_PORT;

			GitCredentialsProvider cp = (GitCredentialsProvider) credentialsProvider;
			if (user == null) {
				CredentialItem.Username u = new CredentialItem.Username();
				if (cp.supports(u) && cp.get(cp.getUri(), u)) {
					user = u.getValue();
				}
			}
			if (pass == null) {
				CredentialItem.Password p = new CredentialItem.Password();
				if (cp.supports(p) && cp.get(cp.getUri(), p)) {
					pass = new String(p.getValue());
				}
			}

			try {
				final Session session = createSession(user, uri.getHost(), port, cp);
				if (pass != null)
					session.setPassword(pass);
				if (credentialsProvider != null && !credentialsProvider.isInteractive()) {
					session.setUserInfo(new CredentialsProviderUserInfo(session, credentialsProvider));
				}

				if (!session.isConnected())
					session.connect(tms);

				return new JschSession(session, uri);
			} catch (JSchException e) {
				throw new TransportException(uri, e.getMessage(), e);
			}
		}
		return null;
	}

	private Session createSession(String user, String host, int port, GitCredentialsProvider cp) throws JSchException {
		JSch jsch = new JSch();
		knownHosts(jsch, cp.getKnownHosts());
		identity(jsch, cp.getPrivateKey(), cp.getPublicKey(), cp.getPassphrase());
		return jsch.getSession(user, host);
	}

	private static void knownHosts(final JSch sch, String knownHosts) throws JSchException {
		try {
			final InputStream in = new StringBufferInputStream(knownHosts);
			try {
				sch.setKnownHosts(in);
			} finally {
				in.close();
			}
		} catch (IOException e) {
			// no known hosts
		}
	}

	private static void identity(final JSch sch, byte[] prvkey, byte[] pubkey, byte[] passphrase) throws JSchException {
		if (prvkey != null && prvkey.length > 0) {
			sch.addIdentity("identity", prvkey, pubkey, passphrase);
		}
	}
}
