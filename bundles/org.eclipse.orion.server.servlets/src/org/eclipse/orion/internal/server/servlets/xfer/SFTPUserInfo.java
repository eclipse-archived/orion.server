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
package org.eclipse.orion.internal.server.servlets.xfer;

import com.jcraft.jsch.UserInfo;

/**
 * Implementation of jsch's UserInfo for purpose of SFTP import and export support.
 * This implementation just hard-codes the credential information and never prompts.
 */
public class SFTPUserInfo implements UserInfo {

	private final String passphrase;
	private final String password;

	public SFTPUserInfo(String password, String passphrase) {
		this.password = password;
		this.passphrase = passphrase;

	}

	public String getPassphrase() {
		return passphrase;
	}

	public String getPassword() {
		return password;
	}

	public boolean promptPassphrase(String message) {
		return true;
	}

	public boolean promptPassword(String message) {
		return true;
	}

	public boolean promptYesNo(String message) {
		//continue connecting to unknown host
		return true;
	}

	public void showMessage(String message) {
		//not needed
	}

}