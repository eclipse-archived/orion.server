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

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSchException;

/**
 * This exception should be thrown when host fingerprint is not added to known hosts.
 *
 */
public class HostFingerprintException extends JSchException {

	private static final long serialVersionUID = -8889137881831717667L;
	private HostKey hostkey;

	public HostFingerprintException(String host, byte[] key) {
		super();
		try {
			this.hostkey = new HostKey(host, key);
		} catch (JSchException e) {
			//no hostkey
		}
	}

	/**
	 * Contains a {@link HostKey} information about a host that is not added to known hosts.
	 * @return
	 */
	public HostKey getHostkey() {
		return hostkey;
	}

}
