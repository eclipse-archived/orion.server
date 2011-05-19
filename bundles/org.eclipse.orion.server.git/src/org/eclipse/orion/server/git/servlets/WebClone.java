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
package org.eclipse.orion.server.git.servlets;

import java.net.URI;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A git clone created in Orion.
 */
public class WebClone {

	private String id;
	private URI contentLocation;
	private URIish uriish;
	private String name;

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return this.id;
	}

	/**
	 * Sets the location of the contents of this clone. The location is an
	 * absolute URI referencing to the filesystem.
	 */
	public void setContentLocation(URI contentURI) {
		this.contentLocation = contentURI;
	}

	/**
	 * Returns the location of the contents of this clone. The result is an
	 * absolute URI in the filesystem.
	 * 
	 * @return The location of the contents of this clone
	 */
	public URI getContentLocation() {
		return this.contentLocation;
	}

	/**
	 * Sets the git URL of this clone.
	 */
	public void setUrl(URIish gitURI) {
		this.uriish = gitURI;
	}

	/**
	 * Returns the URL of the git repository.
	 * <p>
	 * This method never returns null.
	 * </p>
	 * 
	 * @return The URL of the git repository
	 */
	public String getUrl() {
		return this.uriish.toString();
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public static JSONObject toJSON(WebClone clone, URI parentLocation) {
		JSONObject result = new JSONObject();
		try {
			result.put(ProtocolConstants.KEY_ID, clone.getId());
			result.put(ProtocolConstants.KEY_NAME, clone.getName());
			result.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(parentLocation, "file/" + clone.getId())); //$NON-NLS-1$
			result.put(ProtocolConstants.KEY_CONTENT_LOCATION, clone.getContentLocation().toString());
		} catch (JSONException e) {
			// can't happen because key and value are well-formed
		}
		return result;
	}

}
