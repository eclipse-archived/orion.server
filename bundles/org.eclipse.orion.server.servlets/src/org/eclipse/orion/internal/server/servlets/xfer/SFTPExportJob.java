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

import com.jcraft.jsch.*;

import java.io.*;
import java.util.*;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.osgi.util.NLS;

/**
 * Implements export from workspace over SFTP.
 */
public class SFTPExportJob extends SFTPTransferJob {

	public SFTPExportJob(String userRunningTask, File localFile, String host, int port, Path remotePath, String user, String passphrase, List<String> options) {
		super(userRunningTask, localFile, host, port, remotePath, user, passphrase, options);
	}

	protected void doTransferFile(ChannelSftp channel, IPath remotePath, File localFile) throws IOException, SftpException {
		if (shouldSkip(channel, remotePath, localFile))
			return;
		//on export, copy the local file to the remote destination
		channel.put(new FileInputStream(localFile), remotePath.toString());
	}

	/**
	 * Check if we should skip writing this file due to timestamp checks and overwrite options.
	 * @throws IOException If the operation should abort completely
	 */
	private boolean shouldSkip(ChannelSftp channel, IPath remotePath, File localFile) throws IOException {
		SftpATTRS remoteAttributes;
		try {
			remoteAttributes = channel.stat(remotePath.toString());
		} catch (SftpException e) {
			//remote file doesn't exist, so we need to traverse it.
			return false;
		}
		//abort the entire import if we have a collision and no-overwrite is specified
		if (getOptions().contains(ProtocolConstants.OPTION_NO_OVERWRITE)) {
			//give path relative to root in error message
			throw new IOException(NLS.bind("Remote file exists: {0}", remotePath.removeFirstSegments(remoteRoot.segmentCount()).toString()));
		}
		//time is expressed as seconds since the epoch
		int localMTime = (int) (localFile.lastModified() / 1000L);
		int remoteMTime = remoteAttributes.getMTime();

		//check if we should skip overwrite of newer files
		if (getOptions().contains(ProtocolConstants.OPTION_OVERWRITE_OLDER) && remoteMTime > localMTime)
			return true;

		//skip file if unchanged
		if (localMTime == remoteMTime && localFile.length() == remoteAttributes.getSize())
			return true;
		return false;
	}

	@Override
	protected void transferDirectory(ChannelSftp channel, IPath remotePath, File localFile) throws SftpException, IOException {
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
		addTaskTotal(localFiles.length);
		for (File localChild : localChildren) {
			String childName = localChild.getName();
			if (shouldSkip(childName)) {
				taskItemLoaded();
				continue;
			}
			IPath remoteChild = remotePath.append(childName);
			if (localChild.isDirectory()) {
				transferDirectory(channel, remoteChild, localChild);
			} else {
				doTransferFile(channel, remoteChild, localChild);
			}
			taskItemLoaded();
		}
	}
}
