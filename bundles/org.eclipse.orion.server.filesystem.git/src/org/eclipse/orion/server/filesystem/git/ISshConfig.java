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
package org.eclipse.orion.server.filesystem.git;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

public interface ISshConfig {
	public String[] getKnownHosts(String orionUser);
	public CredentialsProvider getCredentialsProvider(String orionUser, URIish uri);
	public KeysCredentials[] getKeysCredentials(String orionUser, URIish uri);
}
