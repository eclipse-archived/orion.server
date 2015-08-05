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

import java.util.Locale;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.orion.internal.server.core.metastore.SimpleUserPasswordUtil;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.MetadataInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.json.JSONObject;

public class GitCredentialsProvider extends UsernamePasswordCredentialsProvider {

	private URIish uri;
	private String remoteUser;
	private String knownHosts;
	private byte[] privateKey;
	private byte[] publicKey;
	private byte[] passphrase;

	public GitCredentialsProvider(URIish uri, String remoteUser, String username, char[] password, String knownHosts) {
		super(username, password);
		this.remoteUser = remoteUser;
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

	@Override
	public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
		for (CredentialItem item : items) {
			/*
			 * If there aren't credentials of any kind and the repository is hosted at GitHub
			 * then check for a pre-set GitHub token and use it if present.
			 */
			if (item instanceof CredentialItem.Username) {
				if ((this.privateKey == null || this.privateKey.length == 0) && (this.publicKey == null || this.publicKey.length == 0) && (this.passphrase == null || this.passphrase.length == 0)) {
					CredentialItem.Username u = new CredentialItem.Username();
					CredentialItem.Password p = new CredentialItem.Password();
					super.get(uri, u, p);
					if ((u.getValue() == null || u.getValue().length() == 0) && (p.getValue() == null || p.getValue().length == 0)) {
						if (uri != null) {
							if (this.remoteUser != null) {
								try {
									MetadataInfo info = OrionConfiguration.getMetaStore().readUser(remoteUser);
									String property = info.getProperty(UserConstants.GITHUB_ACCESS_TOKEN);
									String token = null;
									try {
										JSONObject tokens = new JSONObject(SimpleUserPasswordUtil.decryptPassword(property));
										token = tokens.optString(uri.getHost());
									} catch (Exception e) {
										if (property != null && property.length() > 0 && GitConstants.KEY_GITHUB_HOST.equals(uri.getHost())) {
											/*
											 * Backwards-compatibility: This value is still in the old format, which was
											 * a plain string representing the user's token for github.com specifically.
											 */
											token = property;
										}
									}
									if (token != null) {
										((CredentialItem.Username)item).setValue(token);
										continue;
									}
								} catch (CoreException e) {}
							}
						}
					}
				}
			}
			if (super.supports(item)) {
				super.get(uri, item);
			} else if (item instanceof CredentialItem.StringType) {
				if (item.getPromptText().toLowerCase(Locale.ENGLISH).contains("passphrase") && passphrase != null && passphrase.length > 0) {
					((CredentialItem.StringType) item).setValue(new String(passphrase));
				} else {
					((CredentialItem.StringType) item).setValue("");
				}
			} else if (item instanceof CredentialItem.CharArrayType) {
				((CredentialItem.CharArrayType) item).setValue(new char[0]);
			} else {
				throw new UnsupportedCredentialItem(uri, item.getPromptText());
			}
		}
		return true; // we assume that user provided all credentials that are needed
	}
}
