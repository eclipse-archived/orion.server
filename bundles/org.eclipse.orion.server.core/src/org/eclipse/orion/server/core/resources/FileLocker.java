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
package org.eclipse.orion.server.core.resources;

import java.io.*;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import org.eclipse.orion.server.core.*;
import org.eclipse.osgi.util.NLS;

/**
 * Lock the {@link File} passed in for this process using {@link FileLock}.
 */
public class FileLocker {
	private File lockFile;
	private RandomAccessFile raFile = null;
	private FileLock lock = null;
	private static final boolean locking = Boolean.parseBoolean(PreferenceHelper.getString(ServerConstants.CONFIG_FILE_CONTENT_LOCKING));

	/**
	 * Create the locker.
	 * 
	 * @param toLock The file to lock.  It will be created if it doesn't already
	 * exist.
	 */
	public FileLocker(File toLock) {
		this.lockFile = toLock;
	}

	/**
	 * Try and lock the file.  Return <code>true</code> if it now has the lock, and 
	 * <code>false</code> if it did not acquire the lock.
	 * <p>
	 * If this acquires the lock on the file, the caller must use {@link #release()} 
	 * to release the lock.
	 * </p>
	 * 
	 * @return <code>true</code> if it acquired the lock, false otherwise.
	 * @throws IOException
	 */
	public synchronized boolean tryLock() throws IOException {
		if (!locking) {
			return true;
		}
		lockFile.createNewFile();
		raFile = new RandomAccessFile(lockFile, "rw"); //$NON-NLS-1$
		try {
			lock = raFile.getChannel().tryLock(0, 1L, false);
		} catch (IOException ioe) {
			// produce a more specific message for clients
			String specificMessage = NLS.bind("An error occurred while locking file \"{0}\": \"{1}\". A common reason is that the file system or Runtime Environment does not support file locking for that location.", new Object[] {lock, ioe.getMessage()});
			throw new IOException(specificMessage);
		} catch (OverlappingFileLockException e) {
			// handle it as null result
			lock = null;
		} finally {
			if (lock == null) {
				IOUtilities.safeClose(raFile);
				raFile = null;
			}
		}
		return lock != null;
	}

	/**
	 * Try and lock the file.  This method blocks until it gets the lock.
	 * <p>
	 * The caller must use {@link #release()} to release the lock.
	 * </p>
	 * 
	 * @return <code>true</code> if it acquired the lock, false otherwise.
	 * @throws IOException
	 */
	public synchronized void lock() throws IOException {
		if (!locking) {
			return;
		}
		lockFile.createNewFile();
		raFile = new RandomAccessFile(lockFile, "rw"); //$NON-NLS-1$
		boolean locked = false;
		try {
			do {
				try {
					lock = raFile.getChannel().lock(0, 1L, false);
					locked = true;
				} catch (OverlappingFileLockException e) {
					// another thread within this process probably has the lock
					Thread.sleep(1L);
				}
			} while (!locked);
		} catch (InterruptedException e1) {
			String specificMessage = NLS.bind("An error occurred while locking file \"{0}\": \"{1}\". A common reason is that the file system or Runtime Environment does not support file locking for that location.", new Object[] {lock, e1.getMessage()});
			lock = null;
			throw new IOException(specificMessage);
		} catch (IOException ioe) {
			// produce a more specific message for clients
			String specificMessage = NLS.bind("An error occurred while locking file \"{0}\": \"{1}\". A common reason is that the file system or Runtime Environment does not support file locking for that location.", new Object[] {lock, ioe.getMessage()});
			lock = null;
			throw new IOException(specificMessage);
		} finally {
			if (lock == null) {
				IOUtilities.safeClose(raFile);
				raFile = null;
			}
		}
	}

	/**
	 * The lock is valid if it was acquired and has not been released.
	 * @return <code>true</code> if the lock is valid, <code>false</code> otherwise
	 */
	public synchronized boolean isValid() {
		return !locking || (lock != null && lock.isValid());
	}

	/**
	 * Release the file lock.  If this lock has already been released, this is a no-op.
	 */
	public synchronized void release() {
		if (!locking) {
			return;
		}
		if (lock != null) {
			try {
				lock.release();
			} catch (IOException e) {
				//no-op
			}
		}
		if (raFile != null) {
			IOUtilities.safeClose(raFile);
		}
		lock = null;
		raFile = null;
	}
}
