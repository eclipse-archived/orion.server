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
import com.jcraft.jsch.ChannelSftp.LsEntry;
import java.io.*;
import java.util.Vector;
import org.eclipse.core.runtime.IPath;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.osgi.util.NLS;

/**
 * Implementation of background import into workspace over SFTP.
 */
public class SFTPImportJob extends SFTPTransferJob {

	public SFTPImportJob(File destination, String host, int port, IPath sourcePath, String user, String passphrase) {
		super(destination, host, port, sourcePath, user, passphrase);
	}

	protected void doTransferDirectory(ChannelSftp channel, IPath remotePath, SftpATTRS remoteAttributes, File localFile) throws SftpException, IOException {
		setTaskMessage(NLS.bind("Importing {0}...", host + remotePath.toString()));
		//create the local folder on import
		localFile.mkdirs();
		@SuppressWarnings("unchecked")
		Vector<LsEntry> remoteChildren = channel.ls(remotePath.toString());

		//visit remote children
		for (LsEntry remoteChild : remoteChildren) {
			String childName = remoteChild.getFilename();
			//skip self and parent references
			if (".".equals(childName) || "..".equals(childName)) //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			File localChild = new File(localFile, childName);
			if (remoteChild.getAttrs().isDir()) {
				doTransferDirectory(channel, remotePath.append(childName), remoteChild.getAttrs(), localChild);
			} else {
				doTransferFile(channel, remotePath.append(childName), remoteChild.getAttrs(), localChild);
			}
		}
		synchronizeTimestamp(remoteAttributes, localFile);
	}

	protected void doTransferFile(ChannelSftp channel, IPath remotePath, SftpATTRS remoteAttributes, File localFile) throws IOException, SftpException {
		//skip file if unchanged
		if ((localFile.lastModified() / 1000) == remoteAttributes.getMTime() && localFile.length() == remoteAttributes.getSize())
			return;
		//on import, copy the remote file to the local destination
		IOUtilities.pipe(channel.get(remotePath.toString()), new FileOutputStream(localFile), true, true);
		synchronizeTimestamp(remoteAttributes, localFile);
	}

	private void synchronizeTimestamp(SftpATTRS remoteAttributes, File localFile) {
		localFile.setLastModified((long) remoteAttributes.getMTime() * 1000);
	}

	@Override
	protected void transferDirectory(ChannelSftp channel, IPath remotePath, File localFile) throws SftpException, IOException {
		SftpATTRS attrs = channel.stat(remotePath.toString());
		doTransferDirectory(channel, remotePath, attrs, localFile);
	}
}