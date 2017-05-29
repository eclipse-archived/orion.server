/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

import java.util.Enumeration;
import java.util.Locale;
import java.util.Vector;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class GitCredentialsProvider extends UsernamePasswordCredentialsProvider {

	private URIish uri;
	private String remoteUser;
	private String knownHosts;
	private byte[] privateKey;
	private byte[] publicKey;
	private byte[] passphrase;

	private static Vector<IGitHubTokenProvider> GithubTokenProviders = new Vector<IGitHubTokenProvider>(9);

	public static void AddGitHubTokenProvider(IGitHubTokenProvider value) {
		GithubTokenProviders.add(value);
	}

	public static void RemoveGitHubTokenProvider(IGitHubTokenProvider value) {
		GithubTokenProviders.remove(value);
	}
	
	public static Enumeration<IGitHubTokenProvider> GetGitHubTokenProviders() {
		return GithubTokenProviders.elements();
	}

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
			if (item instanceof CredentialItem.Username || item instanceof CredentialItem.Password) {
				if ((this.privateKey == null || this.privateKey.length == 0) && (this.publicKey == null || this.publicKey.length == 0) && (this.passphrase == null || this.passphrase.length == 0)) {
					CredentialItem.Username u = new CredentialItem.Username();
					CredentialItem.Password p = new CredentialItem.Password();
					super.get(uri, u, p);
					if ((u.getValue() == null || u.getValue().length() == 0) && (p.getValue() == null || p.getValue().length == 0)) {
						if (uri != null) {
							if (this.remoteUser != null) {
								/* see if a GitHub token is available (obviously only applicable for repos hosted at a GitHub) */
								String uriString = uri.toString();
								String token = null;
								for (int i = 0; token == null && i < GithubTokenProviders.size(); i++) {
									token = GithubTokenProviders.get(i).getToken(uriString, remoteUser);
								}
								if (token != null) {
									if (item instanceof CredentialItem.Username) {
										((CredentialItem.Username)item).setValue(token);
									} else {
										((CredentialItem.Password)item).setValue(token.toCharArray());
									}
									continue;
								}
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
