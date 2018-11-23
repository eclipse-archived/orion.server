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
package org.eclipse.orion.server.git.objects;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Stash.TYPE)
public class StashPage extends GitObject {

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				new Property(ProtocolConstants.KEY_CHILDREN), //
				new Property(ProtocolConstants.KEY_PREVIOUS_LOCATION), //
				new Property(ProtocolConstants.KEY_NEXT_LOCATION) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	protected Collection<RevCommit> stash;
	private int pageSize;
	private int page;
	private String messageFilter;

	public StashPage(URI cloneLocation, Repository db, Collection<RevCommit> stash, int page, int pageSize, String filter) {
		super(cloneLocation, db);
		this.stash = stash;
		this.page = Math.max(1, page);
		this.pageSize = Math.max(0, pageSize);
		this.messageFilter = filter;
	}

	@PropertyDescription(name = ProtocolConstants.KEY_CHILDREN)
	protected JSONArray getChildren() throws GitAPIException, JSONException, URISyntaxException, IOException, CoreException {

		int toSkip = (page - 1) * pageSize;
		JSONArray children = new JSONArray();
		int curr = 1, arrLength = 0;

		for (RevCommit revCommit : stash) {
			if (curr++ > toSkip) {

				if (arrLength++ >= pageSize)
					break;

				Stash commit = new Stash(cloneLocation, db, revCommit, null);
				if (messageFilter != null && !messageFilter.equals("")) {
					if (revCommit.getFullMessage().toLowerCase().contains(messageFilter.toLowerCase()))
						children.put(commit.toJSON());
				} else {
					children.put(commit.toJSON());
				}
			}
		}

		return children;
	}

	@PropertyDescription(name = ProtocolConstants.KEY_PREVIOUS_LOCATION)
	protected URI getPreviousPageLocation() throws URISyntaxException {
		if (page > 1) {
			URI location = getLocation();
			String query = String.format("page=%d&pageSize=%d", (page - 1), pageSize); //$NON-NLS-1$
			return new URI(location.getScheme(), location.getAuthority(), location.getPath(), query, location.getFragment());
		}

		return null;
	}

	@PropertyDescription(name = ProtocolConstants.KEY_NEXT_LOCATION)
	protected URI getNextPageLocation() throws URISyntaxException {
		if (hasNextPage()) {
			URI location = getLocation();
			String query = String.format("page=%d&pageSize=%d", (page + 1), pageSize); //$NON-NLS-1$
			return new URI(location.getScheme(), location.getAuthority(), location.getPath(), query, location.getFragment());
		}

		return null;
	}

	protected boolean hasNextPage() {
		return stash.size() > (page * pageSize);
	}

	@Override
	protected URI getLocation() throws URISyntaxException {

		IPath stashPath = new Path(GitServlet.GIT_URI).append(Stash.RESOURCE);

		// clone location is of the form /gitapi/clone/file/{workspaceId}/{projectName}[/{path}]
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);
		stashPath = stashPath.append(clonePath);

		return new URI(cloneLocation.getScheme(), cloneLocation.getAuthority(), stashPath.toString(), null, null);
	}

	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}
}
