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
package org.eclipse.orion.server.authentication.formpersona;

public class PersonaVerificationSuccess {

	private String email, audience, issuer;
	private long expires;

	public PersonaVerificationSuccess(String email, String audience, String issuer, long expires) {
		this.email = email;
		this.audience = audience;
		this.issuer = issuer;
		this.expires = expires;
	}

	public String getEmail() {
		return this.email;
	}

	public String getAudience() {
		return this.audience;
	}

	public long getExpires() {
		return this.expires;
	}

	public String getIssuer() {
		return this.issuer;
	}
}
