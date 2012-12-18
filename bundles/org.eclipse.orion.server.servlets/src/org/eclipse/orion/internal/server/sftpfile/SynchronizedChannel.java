/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.sftpfile;

import com.jcraft.jsch.*;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import java.io.*;
import java.util.Vector;
import org.eclipse.orion.internal.server.core.IOUtilities;

/**
 * A thread safe wrapper for a jsch channel object. Individual channels
 * don't seem to be thread-safe, although jsch session objects have some
 * level of synchronization.
 */
public class SynchronizedChannel {
	private final ChannelSftp inner;

	public SynchronizedChannel(ChannelSftp channel) {
		this.inner = channel;

	}

	public synchronized void disconnect() throws JSchException {
		try {
			inner.disconnect();
		} finally {
			inner.getSession().disconnect();
		}
	}

	public synchronized boolean isConnected() {
		return inner.isConnected() && !inner.isClosed();
	}

	public synchronized SftpATTRS stat(String path) throws SftpException {
		return inner.stat(path);
	}

	public synchronized Vector<LsEntry> ls(String path) throws SftpException {
		@SuppressWarnings("unchecked")
		Vector<LsEntry> result = inner.ls(path);
		return result;
	}

	public synchronized InputStream get(String path) throws SftpException, IOException {
		//we need to transfer the stream to a buffer in case the session is terminated from another thread
		ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		IOUtilities.pipe(inner.get(path), bytesOut, true, true);
		return new ByteArrayInputStream(bytesOut.toByteArray());
	}
}
