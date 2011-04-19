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

import com.jcraft.jsch.*;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This exception should be thrown when host fingerprint is not added to known hosts.
 *
 */
public class HostFingerprintException extends JSchException {

	private static final long serialVersionUID = -8889137881831717667L;
	private static final String HOST = "Host";
	private static final String HOST_FINGERPRINT = "HostFingerprint";
	private static final String HOST_KEY = "HostKey";
	private static final String KEY_TYPE = "KeyType";
	private HostKey hostkey;

	public HostFingerprintException(String host, byte[] key) {
		super("Unknown server key.");
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

	/**
	 * Return information about host and its fingerprint returned by server
	 * @return JSON representation of host and key
	 */
	public JSONObject formJson() {
		JSONObject result = new JSONObject();
		try {
			if (hostkey != null) {
				result.put(HOST, hostkey.getHost());
				result.put(HOST_FINGERPRINT, hostkey.getFingerPrint(new JSch()));
				result.put(HOST_KEY, hostkey.getKey());
				result.put(KEY_TYPE, hostkey.getType());
			}
		} catch (JSONException e) {
			// only when keys are null
		}
		return result;

	}
}
