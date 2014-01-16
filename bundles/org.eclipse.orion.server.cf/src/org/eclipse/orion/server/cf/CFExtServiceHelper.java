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

public class CFExtServiceHelper {

	private ICFExtService authService;

	private static CFExtServiceHelper singleton;

	public static CFExtServiceHelper getDefault() {
		return singleton;
	}

	public void activate() {
		singleton = this;
	}

	public void deactivate() {
		singleton = null;
	}

	public void bind(ICFExtService authService) {
		this.authService = authService;
	}

	public void unbind(ICFExtService authService) {
		this.authService = null;
	}

	public ICFExtService getService() {
		return this.authService;
	}
}
