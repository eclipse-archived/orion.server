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

	@Override
	protected void doTransferDirectory(ChannelSftp channel, IPath remotePath, File localFile) throws SftpException, IOException {
		setTaskMessage(NLS.bind("Importing {0}...", host + remotePath.toString()));
		//create the local folder on import
		localFile.mkdirs();
		super.doTransferDirectory(channel, remotePath, localFile);
	}

	@Override
	protected void doTransferFile(ChannelSftp channel, IPath remotePath, File localFile) throws IOException, SftpException {
		//on import, copy the remote file to the local destination
		IOUtilities.pipe(channel.get(remotePath.toString()), new FileOutputStream(localFile), true, true);
	}
}