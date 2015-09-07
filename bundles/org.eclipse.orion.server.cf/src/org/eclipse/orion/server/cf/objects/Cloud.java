/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.objects;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Cloud.TYPE)
public abstract class Cloud extends CFObject {

	public static final String RESOURCE = "cloud"; //$NON-NLS-1$
	public static final String TYPE = "Cloud"; //$NON-NLS-1$

	private String regionId;
	private URL targetUrl;
	private URL manageUrl;
	private URL uaaUrl;
	private String userId;
	private JSONObject accessToken;
	
	protected Cloud(String regionId, URL targetUrl, URL manageUrl, String userId) {
		super();
		this.regionId = regionId;
		this.targetUrl = targetUrl;
		this.manageUrl = manageUrl;
		this.userId = userId;
	}

	public URL getUrl() {
		return targetUrl;
	}
	
	protected void setUrl(URL url) {
		this.targetUrl = url;
	}
	
	public String getRegion(){
		return regionId;
	}
	
	protected void setRegion(String regionId) {
		this.regionId = regionId;
	}

	public URL getManageUrl() {
		return manageUrl;
	}
	
	protected void setManageUrl(URL manageUrl) {
		this.manageUrl = manageUrl;
	}

	public URL getUaaUrl() {
		return uaaUrl;
	}

	public void setUaaUrl(URL uaaUrl) {
		this.uaaUrl = uaaUrl;
	}

	public JSONObject getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(JSONObject accessToken) {
		this.accessToken = accessToken;
	}

	public String getUserId() {
		return this.userId;
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
		result = prime * result + ((targetUrl == null) ? 0 : targetUrl.hashCode());
		result = prime * result + ((manageUrl == null) ? 0 : manageUrl.hashCode());
		result = prime * result + ((userId == null) ? 0 : userId.hashCode());
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
		if (targetUrl == null) {
			if (other.targetUrl != null)
				return false;
		} else if (!targetUrl.equals(other.targetUrl))
			return false;
		if (manageUrl == null) {
			if (other.manageUrl != null)
				return false;
		} else if (!manageUrl.equals(other.manageUrl))
			return false;
		if (userId == null) {
			if (other.userId != null)
				return false;
		} else if (!userId.equals(other.userId))
			return false;
		return true;
	}
}
