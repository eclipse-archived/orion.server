/*******************************************************************************
 * Copyright (c) 2012, 2014 IBM Corporation and others.
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

import org.eclipse.orion.server.core.IOUtilities;

/**
 * A thread safe wrapper for a jsch channel object. Individual channels
 * are not thread-safe. The choices are either that we have a separate session
 * per thread, or synchronize channel access. This class is used to encapsulate
 * synchronization of channel access.
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

	/**
	 * Client has finished writing to a buffer, and now we can commit the entire
	 * buffer to the channel synchronously.
	 */
	synchronized void doPut(InputStream in, String path) throws SftpException {
		inner.put(in, path);
	}

	public synchronized InputStream get(String path) throws SftpException, IOException {
		//we need to transfer the stream to a buffer in case the session is terminated from another thread
		ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		IOUtilities.pipe(inner.get(path), bytesOut, true, true);
		return new ByteArrayInputStream(bytesOut.toByteArray());
	}

	public synchronized String getHome() throws SftpException {
		return inner.getHome();
	}

	public synchronized boolean isConnected() {
		return inner.isConnected() && !inner.isClosed();
	}

	public synchronized Vector<LsEntry> ls(String path) throws SftpException {
		@SuppressWarnings("unchecked")
		Vector<LsEntry> result = inner.ls(path);
		return result;
	}

	public synchronized void mkdir(String path) throws SftpException {
		inner.mkdir(path);
		return;
	}

	public synchronized OutputStream put(final String path) {
		final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		//let caller write to a buffer so that writing to the channel can be atomic
		return new OutputStream() {

			@Override
			public void close() throws IOException {
				try {
					//now we can commit the write to the channel
					doPut(new ByteArrayInputStream(bytesOut.toByteArray()), path);
				} catch (SftpException e) {
					throw new IOException(e.getMessage());
				}
			}

			@Override
			public void write(byte[] b) throws IOException {
				bytesOut.write(b);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				bytesOut.write(b, off, len);
			}

			@Override
			public void write(int b) throws IOException {
				bytesOut.write(b);
			}
		};
	}

	public synchronized void rm(String path) throws SftpException {
		inner.rm(path);
	}

	public synchronized void rmdir(String path) throws SftpException {
		inner.rmdir(path);
	}

	public synchronized void setStat(String path, SftpATTRS attr) throws SftpException {
		inner.setStat(path, attr);
	}

	public synchronized SftpATTRS stat(String path) throws SftpException {
		return inner.stat(path);
	}
}
