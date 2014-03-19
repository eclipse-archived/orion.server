/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.docker.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class representing a Dockerfile used to create a Docker image.
 *  
 * @author Anthony Hunter
 */
public class DockerFile {

	private String userName;

	public DockerFile(String userName) {
		this.userName = userName;
	}

	/**
	 * Get the tar file containing the Dockerfile that can be sent to the Docker 
	 * build API to create an image. 
	 * 
	 * @return The tar file.
	 */
	public File getTarFile() {
		try {
			// create a temporary folder
			File tempFolder = Files.createTempDirectory(null).toFile();
			tempFolder.deleteOnExit();

			// create the Dockerfile
			File dockerfile = new File(tempFolder, "Dockerfile");
			FileOutputStream fileOutputStream = new FileOutputStream(dockerfile);
			Charset utf8 = Charset.forName("UTF-8");
			OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, utf8);
			outputStreamWriter.write(getDockerfileContent());
			outputStreamWriter.flush();
			outputStreamWriter.close();
			fileOutputStream.close();

			File tarFile = new File(tempFolder, "Dockerfile.tar");

			TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(new FileOutputStream(tarFile));
			tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

			TarArchiveEntry tarEntry = new TarArchiveEntry(dockerfile);
			tarEntry.setName(dockerfile.getName());
			tarArchiveOutputStream.putArchiveEntry(tarEntry);

			FileInputStream fileInputStream = new FileInputStream(dockerfile);
			BufferedInputStream inputStream = new BufferedInputStream(fileInputStream);
			byte[] buffer = new byte[4096];
			int bytes_read;
			while ((bytes_read = inputStream.read(buffer)) != -1) {
				tarArchiveOutputStream.write(buffer, 0, bytes_read);
			}
			inputStream.close();

			tarArchiveOutputStream.closeArchiveEntry();
			tarArchiveOutputStream.close();
			return tarFile;
		} catch (IOException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e);
		}
		return null;
	}

	/**
	 * Get the content of the Dockerfile template located in this bundle.
	 * 
	 * @return the content of the Dockerfile template.
	 */
	protected String getDockerfileContent() {
		try {
			String dockerfileName = "Dockerfile.user";
			URL dockerFileURL = Platform.getBundle("org.eclipse.orion.server.docker").getEntry(dockerfileName);
			File dockerfile = new File(FileLocator.toFileURL(dockerFileURL).getPath());
			
			FileInputStream fileInputStream = new FileInputStream(dockerfile);
			char[] chars = new char[(int) dockerfile.length()];
			Charset utf8 = Charset.forName("UTF-8");
			InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, utf8);
			inputStreamReader.read(chars);
			inputStreamReader.close();
			fileInputStream.close();

			String result = new String(chars);
			result = result.replaceAll("USERNAME", userName);
			return result;
		} catch (IOException e) {
			Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
			logger.error(e.getLocalizedMessage(), e);
		}
		return null;
	}
}
