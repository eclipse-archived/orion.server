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
package org.eclipse.orion.locktest;

import java.io.*;
import java.text.*;
import java.util.Date;
import java.util.Random;

/**
 * Tests to ensure Java file lock actually guarantees exclusive access to a file. Run this test
 * concurrently on multiple compute nodes, pointing at the same file, to ensure that locks
 * are respected.
 * 
 * This test works by writing a token to the lock file while it is locked by this process. The
 * test ensures that the lock is empty when acquired, and contains only the token for the
 * duration that the lock is held. The file is emptied before releasing the lock. In short,
 * the lock file should be non-empty IFF some process believes it has the file locked.
 */
public class FileLockTest {

	private static final DateFormat DEBUG_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
	private static final int DEFAULT_ITERATIONS = 10;
	private static final String PARAM_VERBOSE = "-verbose";
	private static boolean verbose = false;

	private static boolean blockingAcquire(Locker lock) throws IOException {
		int attempts = 0;
		int timeout = 1000;
		long sleepTime = 10;
		while (attempts++ < timeout) {
			if (lock.lock())
				return true;
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				//ignore and loop
			}
		}
		logWarn("Timeout waiting for lock after " + (timeout * sleepTime) + "ms");
		return false;
	}

	private static void doWait(Random rand) {
		try {
			//wait for up to 1 second
			long millis = rand.nextInt(10) * 100L;
			logInfo("Waiting for " + millis + "ms");
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			//ignore
		}
	}

	private static void logInfo(String msg) {
		if (verbose)
			logWarn(msg, null);
	}

	private static void logWarn(String msg) {
		logWarn(msg, null);
	}

	private static void logWarn(String msg, Throwable failure) {
		StringBuffer msgBuf = new StringBuffer(msg.length() + 40);
		DEBUG_FORMAT.format(new Date(), msgBuf, new FieldPosition(0));
		msgBuf.append('-');
		msgBuf.append('[').append(Thread.currentThread()).append(']').append(msg);
		System.out.println(msgBuf.toString());
		if (failure != null)
			failure.printStackTrace();
	}

	public static void main(String[] args) {
		if (args.length == 0) {
			logWarn("Usage: FileLockTest <filePathToLock> [iterations] [-verbose]");
			return;
		}
		File lockFile = null;
		try {
			lockFile = setupLock(args[0]);
		} catch (IOException e) {
			logWarn("Exception during lock setup", e);
		}
		int iterations = DEFAULT_ITERATIONS;
		if (args.length > 1) {
			try {
				iterations = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				//use default iterations
			}
		}
		if (args.length > 2) {
			if (args[2].equalsIgnoreCase(PARAM_VERBOSE))
				verbose = true;
		}
		if (lockFile == null)
			return;
		try {
			Locker lock = new Locker(lockFile);
			runLockTest(lock, iterations);
		} catch (IOException e) {
			logWarn("Exception during lock test", e);
		}

		logInfo("Test complete. Deleting lock file: " + lockFile);
		lockFile.delete();
	}

	private static void runLockTest(Locker lock, int iterations) throws IOException {
		Random rand = new Random();
		final int token = rand.nextInt();
		logWarn("Running lock test with token: " + token + ", iterations: " + iterations);
		for (int i = 0; i < iterations; i++) {
			//wait random time
			doWait(rand);

			//acquire lock
			if (!blockingAcquire(lock))
				return;
			logInfo("Acquired lock: " + lock + " iteration: " + i);
			try {

				//at the moment we acquire the lock, the file should be empty
				RandomAccessFile raf = lock.getFile();
				if (raf.length() != 0) {
					logWarn("FAIL: lock acquired when not empty");
					return;
				}

				//now write our token to file
				logInfo("Writing to file: " + lock);
				raf.seek(0);
				raf.writeInt(token);

				doWait(rand);

				//make sure token is still there
				raf.seek(0);
				int readToken = raf.readInt();
				if (readToken != token) {
					logWarn("FAIL: lock contained token: " + readToken + " but should be: " + token);
					return;
				}

				//finally put file back into empty state
				raf.setLength(0);

			} finally {
				//release lock
				lock.release();
				logInfo("Lock released: " + lock + " iteration: " + i);
			}
		}
		logWarn("Finished lock test with token: " + token + ", iterations: " + iterations);
	}

	private static File setupLock(String fileName) throws IOException {
		File file = new File(fileName);
		if (!file.exists()) {
			logInfo("Creating lock file: " + fileName);
			if (!file.createNewFile()) {
				System.out.println("Failed to create lock file");
				return null;
			}
		}
		return file;
	}
}
