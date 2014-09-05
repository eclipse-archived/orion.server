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
package org.eclipse.orion.server.tests.servlets.git;

import java.io.UnsupportedEncodingException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.tests.AbstractServerTest;
import org.json.JSONException;
import org.json.JSONObject;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;

public class PostGitCloneRequest {

	private String requestURI = AbstractServerTest.SERVER_LOCATION + GitTest.GIT_SERVLET_LOCATION + Clone.RESOURCE + '/';
	private JSONObject body = new JSONObject();

	PostGitCloneRequest setWorkspacePath(IPath workspacePath) throws JSONException {
		if (workspacePath != null)
			body.put(ProtocolConstants.KEY_LOCATION, workspacePath);
		return this;
	}

	PostGitCloneRequest setFilePath(IPath filePath) throws JSONException {
		if (filePath != null && filePath.isAbsolute()) {
			//			assertEquals("file", filePath.segment(0));
			//			assertTrue(filePath.segmentCount() > 1);
			body.put(ProtocolConstants.KEY_PATH, filePath);
		}
		return this;
	}

	PostGitCloneRequest setName(String name) throws JSONException {
		if (name != null)
			body.put(ProtocolConstants.KEY_NAME, name);
		return this;
	}

	PostGitCloneRequest setURIish(URIish uri) throws JSONException {
		body.put(GitConstants.KEY_URL, uri);
		return this;
	}

	PostGitCloneRequest setURIish(String uri) throws JSONException {
		body.put(GitConstants.KEY_URL, uri);
		return this;
	}

	PostGitCloneRequest setKnownHosts(String knownHosts) throws JSONException {
		if (knownHosts != null)
			body.put(GitConstants.KEY_KNOWN_HOSTS, knownHosts);
		return this;
	}

	PostGitCloneRequest setPassword(char[] password) throws JSONException {
		if (password != null)
			body.put(GitConstants.KEY_PASSWORD, new String(password));
		return this;
	}

	PostGitCloneRequest setPrivateKey(byte[] privateKey) throws JSONException {
		if (privateKey != null)
			body.put(GitConstants.KEY_PRIVATE_KEY, new String(privateKey));
		return this;
	}

	PostGitCloneRequest setPublicKey(byte[] publicKey) throws JSONException {
		if (publicKey != null)
			body.put(GitConstants.KEY_PUBLIC_KEY, new String(publicKey));
		return this;
	}

	PostGitCloneRequest setPassphrase(byte[] passphrase) throws JSONException {
		if (passphrase != null)
			body.put(GitConstants.KEY_PASSPHRASE, new String(passphrase));
		return this;
	}

	WebRequest getWebRequest() throws UnsupportedEncodingException {
		WebRequest request = new PostMethodWebRequest(requestURI, IOUtilities.toInputStream(body.toString()), "UTF-8");
		request.setHeaderField(ProtocolConstants.HEADER_ORION_VERSION, "1");
		AbstractServerTest.setAuthentication(request);
		return request;
	}
}
