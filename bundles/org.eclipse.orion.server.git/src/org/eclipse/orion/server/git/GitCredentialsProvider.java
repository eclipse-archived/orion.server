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

import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class GitCredentialsProvider extends UsernamePasswordCredentialsProvider {

	private URIish uri;
	private String knownHosts;
	private byte[] privateKey;
	private byte[] publicKey;
	private byte[] passphrase;

	public GitCredentialsProvider(URIish uri, String username, char[] password, String knownHosts) {
		super(username, password);
		this.uri = uri;
		this.knownHosts = knownHosts;
	}

	public URIish getUri() {
		return uri;
	}

	public String getKnownHosts() {
		return knownHosts;
	}

	public byte[] getPrivateKey() {
		return privateKey;
	}

	public byte[] getPublicKey() {
		return publicKey;
	}

	public byte[] getPassphrase() {
		return passphrase;
	}

	public void setUri(URIish uri) {
		this.uri = uri;
	}

	public void setPrivateKey(byte[] privateKey) {
		this.privateKey = privateKey;
	}

	public void setPublicKey(byte[] publicKey) {
		this.publicKey = publicKey;
	}

	public void setPassphrase(byte[] passphrase) {
		this.passphrase = passphrase;
	}
}
