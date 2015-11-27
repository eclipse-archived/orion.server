/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
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
import com.jcraft.jsch.ChannelSftp.LsEntry;

import java.io.*;
import java.util.List;
import java.util.Vector;

import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.osgi.util.NLS;

/**
 * Implementation of background import into workspace over SFTP.
 */
public class SFTPImportJob extends SFTPTransferJob {

	public SFTPImportJob(String userRunningTask, File destination, String host, int port, IPath sourcePath, String user, String passphrase, List<String> options) {
		super(userRunningTask, destination, host, port, sourcePath, user, passphrase, options);
	}

	protected void doTransferDirectory(ChannelSftp channel, IPath remotePath, SftpATTRS remoteAttributes, File localFile) throws SftpException, IOException {
		//create the local folder on import
		localFile.mkdirs();
		@SuppressWarnings("unchecked")
		Vector<LsEntry> remoteChildren = channel.ls(remotePath.toString());
		addTaskTotal(remoteChildren.size());

		//visit remote children
		for (LsEntry remoteChild : remoteChildren) {
			String childName = remoteChild.getFilename();
			if (shouldSkip(childName)) {
				taskItemLoaded();
				continue;
			}
			File localChild = new File(localFile, childName);
			if (remoteChild.getAttrs().isDir()) {
				doTransferDirectory(channel, remotePath.append(childName), remoteChild.getAttrs(), localChild);
			} else {
				doTransferFile(channel, remotePath.append(childName), remoteChild.getAttrs(), localChild);
			}
			taskItemLoaded();
		}
		synchronizeTimestamp(remoteAttributes, localFile);
	}

	protected void doTransferFile(ChannelSftp channel, IPath remotePath, SftpATTRS remoteAttributes, File localFile) throws IOException, SftpException {
		if (shouldSkip(remotePath, remoteAttributes, localFile))
			return;

		//on import, copy the remote file to the local destination
		IOUtilities.pipe(channel.get(remotePath.toString()), new FileOutputStream(localFile), true, true);
		synchronizeTimestamp(remoteAttributes, localFile);
	}

	/**
	 * Check if we should skip writing this file due to timestamp checks and overwrite options.
	 * @throws IOException If the operation should abort completely
	 */
	private boolean shouldSkip(IPath remotePath, SftpATTRS remoteAttributes, File localFile) throws IOException {
		//abort the entire import if we have a collision and no-overwrite is specified
		if (getOptions().contains(ProtocolConstants.OPTION_NO_OVERWRITE) && localFile.exists()) {
			IPath localPath = remotePath.removeFirstSegments(remoteRoot.segmentCount());
			throw new IOException(NLS.bind("Local file exists: {0}", localPath.toString()));
		}
		//time is expressed as seconds since the epoch
		int localMTime = (int) (localFile.lastModified() / 1000L);
		int remoteMTime = remoteAttributes.getMTime();

		//check if we should skip overwrite of newer files
		if (getOptions().contains(ProtocolConstants.OPTION_OVERWRITE_OLDER) && localMTime > remoteMTime)
			return true;

		//skip file if unchanged
		if (localMTime == remoteMTime && localFile.length() == remoteAttributes.getSize())
			return true;
		return false;
	}

	private void synchronizeTimestamp(SftpATTRS remoteAttributes, File localFile) {
		localFile.setLastModified(remoteAttributes.getMTime() * 1000L);
	}

	@Override
	protected void transferDirectory(ChannelSftp channel, IPath remotePath, File localFile) throws SftpException, IOException {
		SftpATTRS attrs = channel.stat(remotePath.toString());
		doTransferDirectory(channel, remotePath, attrs, localFile);
	}
}