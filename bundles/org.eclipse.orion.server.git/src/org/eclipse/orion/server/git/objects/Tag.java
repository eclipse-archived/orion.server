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
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.json.JSONException;
import org.json.JSONObject;

public class Tag extends GitObject {

	public static final String RESOURCE = "tag"; //$NON-NLS-1$
	public static final String TYPE = "Tag"; //$NON-NLS-1$

	private RevTag tag;
	private URI tagLocation;

	public Tag(URI cloneLocation, Repository db, RevTag tag) throws URISyntaxException, CoreException {
		super(cloneLocation, db);
		this.tag = tag;
	}

	public JSONObject toJSON() throws JSONException, URISyntaxException {
		JSONObject result = new JSONObject();
		result.put(ProtocolConstants.KEY_NAME, tag.getTagName());
		result.put(ProtocolConstants.KEY_CONTENT_LOCATION, getLocation());
		result.put(ProtocolConstants.KEY_TYPE, TYPE);
		return result;
	}

	private URI getLocation() throws URISyntaxException {
		if (tagLocation == null) {
			IPath p = new Path(cloneLocation.getPath());
			p = p.uptoSegment(1).append(RESOURCE).append(tag.getTagName()).addTrailingSeparator().append(p.removeFirstSegments(2));
			tagLocation = new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), p.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
		}
		return tagLocation;
	}
}