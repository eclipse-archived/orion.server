/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

public interface GitHubTokenProvider {
	
	/**
	 * Answers a GitHub token that can be used to authorize interactions with a given
	 * GitHub repository and user, or <code>null</code> if no such token can be provided.
	 * 
	 * @param repositoryUrl the URL of the repository
	 * @param userId the Orion user id
	 */
	public String getToken(String repositoryUrl, String userId);
}
