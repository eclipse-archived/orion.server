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

import org.eclipse.orion.internal.server.servlets.docker.DockerContainer;
import org.eclipse.orion.internal.server.servlets.docker.DockerContainers;
import org.eclipse.orion.internal.server.servlets.docker.DockerImage;
import org.eclipse.orion.internal.server.servlets.docker.DockerImages;
import org.eclipse.orion.internal.server.servlets.docker.DockerResponse;
import org.eclipse.orion.internal.server.servlets.docker.DockerServer;
import org.eclipse.orion.internal.server.servlets.docker.DockerVersion;
import org.junit.Test;

/**
 * Tests for the docker server.
 *
 * @author Anthony Hunter
 */
public class DockerServerTests {
	protected final static String dockerLocation = "http://localhost:9443";

	/**
	 * Test create docker container.
	 * @throws URISyntaxException
	 */
	@Test
	public void testCreateDockerContainer() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerContainer dockerContainer = dockerServer.createDockerContainer("ubuntu", "anthony");
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.CREATED, dockerContainer.getStatusCode());
		System.out.println("Created Docker Container: Container Id " + dockerContainer.getId() + " Image " + dockerContainer.getImage() + " Name " + dockerContainer.getName());
	}

	/**
	 * Test get docker container.
	 * @throws URISyntaxException
	 */
	@Test
	public void testGetDockerContainer() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerContainer dockerContainer = dockerServer.getDockerContainer("admin");
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.OK, dockerContainer.getStatusCode());

		dockerContainer = dockerServer.getDockerContainer("doesnotexist");
		assertEquals(dockerContainer.getStatusMessage(), DockerResponse.StatusCode.NO_SUCH_CONTAINER, dockerContainer.getStatusCode());
	}

	/**
	 * Test get docker containers.
	 * @throws URISyntaxException
	 */
	@Test
	public void testGetDockerContainers() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerContainers dockerContainers = dockerServer.getDockerContainers();
		assertEquals(dockerContainers.getStatusMessage(), DockerResponse.StatusCode.OK, dockerContainers.getStatusCode());
		System.out.println("Docker Containers: ");
		for (DockerContainer dockerContainer : dockerContainers.getContainers()) {
			System.out.println("Container Id " + dockerContainer.getId() + " Image " + dockerContainer.getImage() + " Name " + dockerContainer.getName());
		}
	}

	/**
	 * Test get docker image.
	 * @throws URISyntaxException
	 */
	@Test
	public void testGetDockerImage() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerImage dockerImage = dockerServer.getDockerImage("ubuntu");
		assertEquals(dockerImage.getStatusMessage(), DockerResponse.StatusCode.OK, dockerImage.getStatusCode());

		dockerImage = dockerServer.getDockerImage("doesnotexist");
		assertEquals(dockerImage.getStatusMessage(), DockerResponse.StatusCode.NO_SUCH_IMAGE, dockerImage.getStatusCode());
	}

	/**
	 * Test get docker images.
	 * @throws URISyntaxException
	 */
	@Test
	public void testGetDockerImages() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerImages dockerImages = dockerServer.getDockerImages();
		assertEquals(dockerImages.getStatusMessage(), DockerResponse.StatusCode.OK, dockerImages.getStatusCode());
		System.out.println("Docker Images: ");
		for (DockerImage dockerImage : dockerImages.getImages()) {
			System.out.print("Image Id " + dockerImage.getId() + " Repository " + dockerImage.getRepository() + " Tags { ");
			for (String tag : dockerImage.getTags()) {
				System.out.print(tag + " ");
			}
			System.out.println("}");
		}
	}

	/**
	 * Test get docker version, if this test fails docker is likely not running.
	 * @throws URISyntaxException 
	 */
	@Test
	public void testGetDockerVersion() throws URISyntaxException {
		URI dockerLocationURI = new URI(dockerLocation);
		DockerServer dockerServer = new DockerServer(dockerLocationURI);
		DockerVersion dockerVersion = dockerServer.getDockerVersion();
		assertEquals(dockerVersion.getStatusMessage(), DockerResponse.StatusCode.OK, dockerVersion.getStatusCode());
		String version = dockerVersion.getVersion();
		System.out.println("Docker Server " + dockerLocation + " is running version " + version);
	}
}
