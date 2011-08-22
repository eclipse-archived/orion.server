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
package org.eclipse.orion.server.git.objects;

import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.json.JSONException;
import org.json.JSONObject;

public class Tag extends GitObject {

	public static final String RESOURCE = "tag"; //$NON-NLS-1$
	public static final String TYPE = "Tag"; //$NON-NLS-1$

	private URI baseLocation;
	private RevTag tag;

	public Tag(URI baseLocation, Repository db, RevTag tag) throws URISyntaxException, CoreException {
		super(BaseToCloneConverter.getCloneLocation(baseLocation, BaseToCloneConverter.TAG_LIST), db);
		this.baseLocation = baseLocation;
		this.tag = tag;
	}

	public JSONObject toJSON() throws JSONException {
		JSONObject result = new JSONObject();
		result.put(ProtocolConstants.KEY_NAME, tag.getTagName());
		result.put(ProtocolConstants.KEY_CONTENT_LOCATION, baseLocation);
		result.put(ProtocolConstants.KEY_TYPE, TYPE);
		return result;
	}
}
