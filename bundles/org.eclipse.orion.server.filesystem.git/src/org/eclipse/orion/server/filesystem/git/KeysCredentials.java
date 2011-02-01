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
package org.eclipse.orion.server.filesystem.git;

public class KeysCredentials {
	private final String name;
	private final String publicKey;
	private final String privateKey;
	private final String passphrase;

	public KeysCredentials(String name, String publicKey, String privateKey,
			String passphrase) {
		this.name = name;
		this.publicKey = publicKey;
		this.privateKey = privateKey;
		this.passphrase = passphrase;
	}

	/**
	 * @return name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return public key
	 */
	public String getPublicKey() {
		return publicKey;
	}

	/**
	 * @return private key
	 */
	public String getPrivateKey() {
		return privateKey;
	}

	/**
	 * @return passphrase for private key
	 */
	public String getPassphrase() {
		return passphrase;
	}

}