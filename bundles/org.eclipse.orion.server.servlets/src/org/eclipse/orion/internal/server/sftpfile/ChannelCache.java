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
import java.net.URI;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.xfer.SFTPUserInfo;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.osgi.util.NLS;

/**
 * A class that maintains a small cache of open SFTP channels.
 */
public class ChannelCache {
	/**
	 * Duration to keep the cache alive.
	 */
	private static final long CACHE_TIMEOUT = 60000;
	/**
	 * Only cache one channel at a time for now.
	 */
	private static SynchronizedChannel cache;
	/**
	 * The host that we are currently caching for
	 */
	private static URI cacheHost;

	private static long cacheExpiry;

	/**
	 * Cleans up a channel at the end of an operation. This method MUST NOT
	 * throw exceptions because the caller is trying to open a different unrelated channel.
	 */
	private static void closeChannel() {
		//clear fields first in case of failure
		URI hostToClose = cacheHost;
		SynchronizedChannel channelToClose = cache;
		cache = null;
		cacheHost = null;
		if (channelToClose == null)
			return;
		try {
			channelToClose.disconnect();
		} catch (Exception e) {
			String msg = NLS.bind("Failure closing connection to {0}", hostToClose, e.getMessage()); //$NON-NLS-1$
			LogHelper.log(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e));
		}
	}

	public static synchronized SynchronizedChannel getChannel(URI host) throws CoreException {
		if (isCacheAlive(host))
			return cache;
		//discard the current channel because we will cache a new one below
		closeChannel();

		cache = openChannel(host);
		cacheHost = host;
		return cache;
	}

	private static boolean isCacheAlive(URI host) {
		if (cacheHost == null)
			return false;
		if (!cacheHost.equals(host))
			return false;
		if (!cache.isConnected())
			return false;
		if (System.currentTimeMillis() > cacheExpiry) {
			System.out.println("Cache expired: " + host);
			return false;
		}
		return true;
	}

	/**
	 * Something went wrong, so flush the cached channel for this host.
	 */
	public static synchronized void flush(URI host) {
		if (host != null && host.equals(cacheHost)) {
			System.out.println("Flushing channel to: " + host);
			closeChannel();
		}

	}

	private static SynchronizedChannel openChannel(URI host) throws CoreException {
		System.out.println("Opening channel to: " + host);
		JSch jsch = new JSch();
		try {
			int port = host.getPort();
			if (port < 0)
				port = 22;//default SFTP port
			String user = host.getUserInfo();
			Session session = jsch.getSession(user, host.getHost(), port);
			session.setUserInfo(new SFTPUserInfo("password", "password"));
			//don't require host key to be in orion server's known hosts file
			session.setConfig("StrictHostKeyChecking", "no"); //$NON-NLS-1$ //$NON-NLS-2$
			session.connect();
			ChannelSftp channel = (ChannelSftp) session.openChannel("sftp"); //$NON-NLS-1$
			channel.connect();
			cacheExpiry = System.currentTimeMillis() + CACHE_TIMEOUT;
			return new SynchronizedChannel(channel);
		} catch (Exception e) {
			String msg = NLS.bind("Failure connecting to {0}", host, e.getMessage());
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e));
		}
	}
}
