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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.osgi.util.NLS;

/**
 * Lock the {@link File} passed in for this process using {@link FileLock}.
 */
public class FileLocker {
	private File lockFile;
	private RandomAccessFile raFile = null;
	private int counter = 0;
	private FileLock fileLock = null;
	ReadWriteLock lock = new ReentrantReadWriteLock();
	private static final boolean locking = Boolean.parseBoolean(PreferenceHelper.getString(ServerConstants.CONFIG_FILE_CONTENT_LOCKING));

	public class Lock {
		private boolean isShared;
		public Lock(boolean isShared) {
			super();
			this.isShared = isShared;
		}
		public void release() {
			releaseLock();
			if (this.isShared) {
				lock.readLock().unlock();
			} else {
				lock.writeLock().unlock();
			}
		}
	}

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
	 * Try and lock the file.  This method blocks until it gets the lock.
	 * <p>
	 * The caller must use {@link #release()} to release the lock.
	 * </p>
	 * 
	 * @return <code>true</code> if it acquired the lock, false otherwise.
	 * @throws IOException
	 */
	public Lock lock(boolean shared) throws IOException {
		try {
			if (shared) {
				lock.readLock().lock();
			} else {
				lock.writeLock().lock();
			}
			acquireLock(shared);
		} catch (IOException ioe) {
			if (shared) {
				lock.readLock().unlock();
			} else {
				lock.writeLock().unlock();
			}
			// produce a more specific message for clients
			String specificMessage = NLS.bind("An error occurred while locking file \"{0}\": \"{1}\". A common reason is that the file system or Runtime Environment does not support file locking for that location.", new Object[] {fileLock, ioe.getMessage()});
			fileLock = null;
			throw new IOException(specificMessage, ioe);
		}

		return new Lock(shared);
	}

	private synchronized void acquireLock(boolean shared) throws IOException {
		try {
			boolean locked = false;
			do {	
				if (locking && counter == 0) {
					lockFile.getParentFile().mkdirs();
					lockFile.createNewFile();
					if (raFile == null) {
						raFile = new RandomAccessFile(lockFile, "rw"); //$NON-NLS-1$
					}

					try {
						if (shared) {
							fileLock = raFile.getChannel().lock(0, 1L, true);
						} else {
							fileLock = raFile.getChannel().lock(0, 1L, false);
						}
						locked = true;
					} catch (OverlappingFileLockException e) {
						// another thread within this process probably has the lock
					}
				} else {
					locked = true;
				}
			} while (!locked);
			counter++;
		} finally {
			if (fileLock == null) {
				IOUtilities.safeClose(raFile);
				raFile = null;
			}
		}
	}
	
	private synchronized void releaseLock() {
		if (--counter == 0) {
			if (!locking) {
				return;
			}
			if (fileLock != null) {
				try {
					fileLock.release();
				} catch (IOException e) {
					//no-op
				}
			}
			if (raFile != null) {
				IOUtilities.safeClose(raFile);
			}
			raFile = null;
		}
	}
}
