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
package org.eclipse.orion.internal.server.filesystem.git;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

public class OrionUserCredentialsProvider extends CredentialsProvider {

	private String orionUser;
	private URIish uri;
	private CredentialsProvider delegate;

	public OrionUserCredentialsProvider(String orionUser, URIish uri) {
		this.orionUser = orionUser;
		this.uri = uri;
		delegate = SshConfigManager.getDefault().getCredentialsProvider(
				orionUser, uri);
	}

	public String getOrionUser() {
		return orionUser;
	}

	public URIish getUri() {
		return uri;
	}

	@Override
	public boolean isInteractive() {
		if (delegate != null)
			return delegate.isInteractive();
		return false;
	}

	@Override
	public boolean supports(CredentialItem... items) {
		if (delegate != null)
			return delegate.supports(items);
		return false;
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		if (delegate != null)
			return delegate.get(uri, items);
		return false;
	}

}
