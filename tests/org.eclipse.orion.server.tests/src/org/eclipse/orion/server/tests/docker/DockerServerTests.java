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
package org.eclipse.orion.server.tests.docker;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.orion.internal.server.servlets.docker.DockerResponse;
import org.eclipse.orion.internal.server.servlets.docker.DockerServer;
import org.eclipse.orion.internal.server.servlets.docker.DockerVersion;
import org.junit.Test;

/**
 * Tests for the docker server .
 *
 * @author Anthony Hunter
 */
public class DockerServerTests {
	protected final static String dockerLocation = "http://localhost:9442";

	/**
	 * Test the docker version, if this test fails docker is not running.
	 * @throws URISyntaxException 
	 */
	@Test
	public void testDockerVersion() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerVersion dockerVersion = dockerServer.getDockerVersion();
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		String version = dockerVersion.getVersion();
		System.out.println("Docker Server " + dockerLocation + " is running version " + version);
	}
}
