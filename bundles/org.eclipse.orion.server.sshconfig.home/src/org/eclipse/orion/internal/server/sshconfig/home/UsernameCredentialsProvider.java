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
package org.eclipse.orion.internal.server.sshconfig.home;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Simple {@link CredentialsProvider} that always uses the same information.
 */
public class UsernameCredentialsProvider extends CredentialsProvider {
	private String username;

	/**
	 * Initialize the provider with a single username.
	 *
	 * @param username
	 */
	public UsernameCredentialsProvider(String username) {
		this.username = username;
	}

	@Override
	public boolean isInteractive() {
		return false;
	}

	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username)
				continue;
			else
				return false;
		}
		return true;
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username)
				((CredentialItem.Username) i).setValue(username);
			else
				throw new UnsupportedCredentialItem(uri, i.getPromptText());
		}
		return true;
	}

	/** Destroy the saved username. */
	public void clear() {
		username = null;
	}
}
