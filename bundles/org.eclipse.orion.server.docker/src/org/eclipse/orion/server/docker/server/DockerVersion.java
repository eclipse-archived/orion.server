/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.docker.server;


/**
 * The response received from the Docker Version Remote API call.
 *  
 * @author Anthony Hunter
 */
public class DockerVersion extends DockerResponse {

	public static final String GIT_COMMIT = "GitCommit";

	public static final String GO_VERSION = "GoVersion";

	public static final String VERSION = "Version";

	public static final String VERSION_PATH = "version";

	private String gitCommit = null;

	private String goVersion = null;

	private String version = null;

	public String getGitCommit() {
		return gitCommit;
	}

	public String getGoVersion() {
		return goVersion;
	}

	public String getVersion() {
		return version;
	}

	public void setGitCommit(String gitCommit) {
		this.gitCommit = gitCommit;
	}

	public void setGoVersion(String goVersion) {
		this.goVersion = goVersion;
	}

	public void setVersion(String version) {
		this.version = version;
	}

}
