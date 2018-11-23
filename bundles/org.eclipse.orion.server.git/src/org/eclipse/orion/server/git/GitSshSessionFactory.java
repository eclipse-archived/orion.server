/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProviderUserInfo;
import org.eclipse.jgit.transport.JschSession;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.server.jsch.SessionHandler;

import com.jcraft.jsch.JSchException;

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
				final SessionHandler session = new SessionHandler(user, uri.getHost(), port, cp.getKnownHosts(), cp.getPrivateKey(), cp.getPublicKey(),
						cp.getPassphrase());
				if (pass != null)
					session.setPassword(pass);
				if (!credentialsProvider.isInteractive()) {
					session.setUserInfo(new CredentialsProviderUserInfo(session.getSession(), credentialsProvider));
				}

				session.connect(tms);

				return new JschSession(session.getSession(), uri);
			} catch (JSchException e) {
				throw new TransportException(uri, e.getMessage(), e);
			}
		}
		return null;
	}

}
