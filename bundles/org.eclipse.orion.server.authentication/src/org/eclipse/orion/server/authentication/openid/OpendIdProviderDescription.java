/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.authentication.openid;

public class OpendIdProviderDescription {

	private String authSite;
	private String image;
	private String name;

	public String getAuthSite() {
		return authSite;
	}

	public void setAuthSite(String authSite) {
		this.authSite = authSite;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public OpendIdProviderDescription(String authSite, String image, String name) {
		this.authSite = authSite;
		this.image = image;
		this.name = name;
	}

	public OpendIdProviderDescription() {
	}

}
