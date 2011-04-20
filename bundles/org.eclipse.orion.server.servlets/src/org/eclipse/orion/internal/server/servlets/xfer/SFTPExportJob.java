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
package org.eclipse.orion.internal.server.servlets.xfer;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import java.io.*;
import java.util.*;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;

/**
 * Implements export from workspace over SFTP.
 */
public class SFTPExportJob extends SFTPTransferJob {

	public SFTPExportJob(File localFile, String host, int port, Path remotePath, String user, String passphrase) {
		super(localFile, host, port, remotePath, user, passphrase);
	}

	@Override
	protected void transferDirectory(ChannelSftp channel, IPath remotePath, File localFile) throws SftpException, IOException {
		setTaskMessage(NLS.bind("Exporting {0}...", host + remotePath.toString()));
		try {
			//create the remote folder on export
			channel.mkdir(remotePath.toString());
		} catch (SftpException e) {
			//mkdir failure likely means the folder already exists
		}
		//visit local children
		List<File> localChildren = new ArrayList<File>();
		File[] localFiles = localFile.listFiles();
		if (localFiles != null)
			localChildren.addAll(Arrays.asList(localFiles));
		for (File localChild : localChildren) {
			String childName = localChild.getName();
			if (shouldSkip(childName))
				continue;
			IPath remoteChild = remotePath.append(childName);
			if (localChild.isDirectory()) {
				transferDirectory(channel, remoteChild, localChild);
			} else {
				doTransferFile(channel, remoteChild, localChild);
			}
		}
	}

	protected void doTransferFile(ChannelSftp channel, IPath remotePath, File localFile) throws IOException, SftpException {
		//on export, copy the local file to the remote destination
		String remote = remotePath.toString();
		channel.put(new FileInputStream(localFile), remote);
	}
}
