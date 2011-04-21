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
package org.eclipse.orion.server.jsch;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

public class JSchUtil {

	public static void knownHosts(final JSch sch, String knownHosts) throws JSchException {
		LazyKnownHosts hRepo = new LazyKnownHosts(sch, knownHosts);
		sch.setHostKeyRepository(hRepo);
		JSch.setConfig("StrictHostKeyChecking", "yes");
	}

	public static void identity(final JSch sch, byte[] prvkey, byte[] pubkey, byte[] passphrase) throws JSchException {
		if (prvkey != null && prvkey.length > 0) {
			sch.addIdentity("identity", prvkey, pubkey != null && pubkey.length > 0 ? pubkey : null, passphrase);
		}
	}
}
