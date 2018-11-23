/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.jsch;

import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

/**
 * Use this handler to obtain and connect {@link Session}.
 *
 */
public class SessionHandler {

	private Session session;
	private JSch jSch;

	/**
	 * Creates a session identified via private key.
	 * 
	 * @param user
	 * @param host
	 * @param port
	 * @param knownHosts
	 * @param privateKey
	 * @param publicKey
	 * @param passphrase
	 * @throws JSchException
	 */
	public SessionHandler(String user, String host, int port, String knownHosts, byte[] privateKey, byte[] publicKey, byte[] passphrase) throws JSchException {
		jSch = new JSch();
		JSchUtil.knownHosts(jSch, knownHosts);
		JSchUtil.identity(jSch, privateKey, publicKey, passphrase);
		this.session = jSch.getSession(user, host, port);
		// we don't prompt for password on the server so we never want to retry on authorization failure
		this.session.setConfig("MaxAuthTries", "1"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void setPassword(String password) {
		session.setPassword(password);
	}

	public void setUserInfo(UserInfo userInfo) {
		session.setUserInfo(userInfo);
	}

	public Session getSession() {
		return session;
	}

	public JSch getjSch() {
		return jSch;
	}

	/**
	 * Connects this session and adds custom error handling.
	 * 
	 * @param tms
	 * @throws JSchException
	 */
	public void connect(int tms) throws JSchException {
		try {
			if (!session.isConnected())
				session.connect(tms);
		} catch (JSchException e) {
			if (jSch.getHostKeyRepository() instanceof LazyKnownHosts) {
				LazyKnownHosts hostsRepo = (LazyKnownHosts) jSch.getHostKeyRepository();
				if (hostsRepo.getLastStatus() != HostKeyRepository.OK) {
					throw new HostFingerprintException(hostsRepo.getLastUnknownkedHost(), hostsRepo.getLastUnknownKey());
				}
			}
			throw e;
		}
	}
}
