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
import java.io.File;
import java.io.IOException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * Implements export from workspace over SFTP.
 */
public class SFTPExportJob extends SFTPTransferJob {

	public SFTPExportJob(File localFile, String host, int port, Path remotePath, String user, String passphrase) {
		super(localFile, host, port, remotePath, user, passphrase);
	}

	@Override
	void doTransferFile(ChannelSftp channel, IPath remotePath, File localFile) throws IOException, SftpException {
	}

}
