/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf;

public class CFAuthServiceHelper {

	private ICFAuthService authService;

	private static CFAuthServiceHelper singleton;

	public static CFAuthServiceHelper getDefault() {
		return singleton;
	}

	public void activate() {
		singleton = this;
	}

	public void deactivate() {
		singleton = null;
	}

	public void bind(ICFAuthService authService) {
		this.authService = authService;
	}

	public void unbind(ICFAuthService authService) {
		this.authService = null;
	}

	public ICFAuthService getService() {
		return this.authService;
	}
}
