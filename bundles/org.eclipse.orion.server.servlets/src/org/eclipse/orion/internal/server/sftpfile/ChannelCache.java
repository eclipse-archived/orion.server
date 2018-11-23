/*******************************************************************************
 * Copyright (c) 2012, 2013 IBM Corporation and others.
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
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.sftp"); //$NON-NLS-1$
		if (isCacheAlive(host, logger))
			return cache;
		//discard the current channel because we will cache a new one below
		closeChannel();

		cache = openChannel(host, logger);
		cacheHost = host;
		return cache;
	}

	private static boolean isCacheAlive(URI host, Logger logger) {
		if (cacheHost == null)
			return false;
		if (!cacheHost.equals(host))
			return false;
		if (!cache.isConnected())
			return false;
		if (System.currentTimeMillis() > cacheExpiry) {
			if (logger.isInfoEnabled())
				logger.info("Cache expired: " + host); //$NON-NLS-1$ 
			return false;
		}
		return true;
	}

	/**
	 * Something went wrong, so flush the cached channel for this host.
	 */
	public static synchronized void flush(URI host) {
		if (host != null && host.equals(cacheHost)) {
			closeChannel();
		}
	}

	private static SynchronizedChannel openChannel(URI host, Logger logger) throws CoreException {
		if (logger.isInfoEnabled())
			logger.info("Opening channel to: " + host); //$NON-NLS-1$ 
		JSch jsch = new JSch();
		int port = host.getPort();
		if (port < 0)
			port = 22;//default SFTP port
		String user = host.getUserInfo();
		//standard URI format of user:password
		String[] userParts = user != null ? user.split(":") : null; //$NON-NLS-1$
		if (userParts == null || userParts.length != 2)
			throw authFail(host);
		try {
			Session session = jsch.getSession(userParts[0], host.getHost(), port);
			String password = userParts[1];
			session.setPassword(password);
			//don't require host key to be in orion server's known hosts file
			session.setConfig("StrictHostKeyChecking", "no"); //$NON-NLS-1$ //$NON-NLS-2$
			session.connect();
			ChannelSftp channel = (ChannelSftp) session.openChannel("sftp"); //$NON-NLS-1$
			channel.connect();
			cacheExpiry = System.currentTimeMillis() + CACHE_TIMEOUT;
			return new SynchronizedChannel(channel);
		} catch (Exception e) {
			//Message is hard-coded in jsch implementation and is the only way to distinguish from other errors
			if ("Auth fail".equals(e.getMessage())) //$NON-NLS-1$
				throw authFail(host);
			String msg = NLS.bind("Failure connecting to {0}", host, e.getMessage());
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e));
		}
	}

	/**
	 * Returns a core exception indicating failure to authenticate with the given host.
	 */
	private static CoreException authFail(URI host) {
		return new AuthCoreException(host.getHost());
	}
}
