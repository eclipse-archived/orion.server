/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.objects;

import java.net.*;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Cloud.TYPE)
public class Cloud extends CFObject {

	public static final String RESOURCE = "cloud"; //$NON-NLS-1$
	public static final String TYPE = "Cloud"; //$NON-NLS-1$

	private URL apiUrl;
	private URL manageUrl;
	private JSONObject accessToken;

	public Cloud(URL apiUrl, URL manageUrl) {
		super();
		this.apiUrl = apiUrl;
		this.manageUrl = manageUrl;
	}

	public URL getApiUrl() {
		return apiUrl;
	}

	public void setApiUrl(URL apiUrl) {
		this.apiUrl = apiUrl;
	}

	public URL getManageUrl() {
		return manageUrl;
	}

	public void setManageUrl(URL manageUrl) {
		this.manageUrl = manageUrl;
	}

	public JSONObject getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(JSONObject accessToken) {
		this.accessToken = accessToken;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((apiUrl == null) ? 0 : apiUrl.hashCode());
		result = prime * result + ((manageUrl == null) ? 0 : manageUrl.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Cloud other = (Cloud) obj;
		if (apiUrl == null) {
			if (other.apiUrl != null)
				return false;
		} else if (!apiUrl.equals(other.apiUrl))
			return false;
		if (manageUrl == null) {
			if (other.manageUrl != null)
				return false;
		} else if (!manageUrl.equals(other.manageUrl))
			return false;
		return true;
	}
}
