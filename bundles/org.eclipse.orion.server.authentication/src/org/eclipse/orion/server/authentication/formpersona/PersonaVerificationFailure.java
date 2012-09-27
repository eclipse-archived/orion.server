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

/**
 * 
 */
public class PersonaVerificationFailure {
	private String reason;

	public PersonaVerificationFailure(String reason) {
		this.reason = reason;
	}

	public String getReason() {
		return reason;
	}

}
