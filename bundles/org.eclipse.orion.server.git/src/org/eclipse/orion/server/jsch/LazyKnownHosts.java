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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;

/**
 * Use this repository instead of standard {@link HostKeyRepository} to record the last checked host fingerprint. If the last status recorded as
 * {@link #lastStatus}, if check in the repository is different than {@link HostKeyRepository#OK} the host and its key are recorded as {@link #lastUnknownHost}
 * and {@link #lastUnknownKey}.
 *
 */
public class LazyKnownHosts implements HostKeyRepository {

	private HostKeyRepository repo;

	private String lastUnknownHost = null;
	private byte[] lastUnknownKey = null;
	private int lastStatus = OK;

	LazyKnownHosts(JSch jsch, String knownHosts) throws JSchException {
		if (knownHosts != null) {
			try {
				final InputStream in = new ByteArrayInputStream(knownHosts.getBytes("UTF8"));
				try {
					jsch.setKnownHosts(in);
				} finally {
					in.close();
				}
			} catch (IOException e) {
				// no known hosts
			}
		}
		this.repo = jsch.getHostKeyRepository();

	}

	@Override
	public int check(String host, byte[] key) {
		lastStatus = repo.check(host, key);

		if (lastStatus != OK) {
			lastUnknownHost = host;
			lastUnknownKey = key;
		} else {
			lastUnknownHost = null;
			lastUnknownKey = null;
		}
		return lastStatus;
	}

	@Override
	public void add(HostKey hostkey, UserInfo ui) {
		repo.add(hostkey, ui);
	}

	@Override
	public void remove(String host, String type) {
		repo.remove(host, type);
	}

	@Override
	public void remove(String host, String type, byte[] key) {
		repo.remove(host, type, key);
	}

	@Override
	public String getKnownHostsRepositoryID() {
		return "LAZY_" + repo.getKnownHostsRepositoryID();
	}

	@Override
	public HostKey[] getHostKey() {
		return repo.getHostKey();
	}

	@Override
	public HostKey[] getHostKey(String host, String type) {
		return repo.getHostKey();
	}

	public String getLastUnknownkedHost() {
		return lastUnknownHost;
	}

	public byte[] getLastUnknownKey() {
		return lastUnknownKey;
	}

	public int getLastStatus() {
		return lastStatus;
	}

}
