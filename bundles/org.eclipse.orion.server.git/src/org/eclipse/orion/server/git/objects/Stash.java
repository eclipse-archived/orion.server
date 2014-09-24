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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Stash.TYPE)
public class Stash extends Commit {

	public static final String RESOURCE = "stash"; //$NON-NLS-1$
	public static final String TYPE = "StashCommit"; //$NON-NLS-1$

	protected static final ResourceShape EXTENDED_RESOURCE_SHAPE = new ResourceShape();
	{
		/* property inheritance */
		EXTENDED_RESOURCE_SHAPE.setProperties(DEFAULT_RESOURCE_SHAPE.getProperties());
		EXTENDED_RESOURCE_SHAPE.addProperty(new Property(GitConstants.KEY_STASH_APPLY_LOCATION));
		EXTENDED_RESOURCE_SHAPE.addProperty(new Property(GitConstants.KEY_STASH_DROP_LOCATION));
	}

	public Stash(URI cloneLocation, Repository db, RevCommit revCommit, String pattern) {
		super(cloneLocation, db, revCommit, pattern);
	}

	protected URI createStashLocation() throws URISyntaxException {

		IPath stashPath = new Path(GitServlet.GIT_URI).append(Stash.RESOURCE);
		stashPath = stashPath.append(getName());

		// clone location is of the form /gitapi/clone/file/{workspaceId}/{projectName}[/{path}]
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);
		stashPath = stashPath.append(clonePath);

		return new URI(cloneLocation.getScheme(), cloneLocation.getAuthority(), stashPath.toString(), null, null);
	}

	@PropertyDescription(name = GitConstants.KEY_STASH_APPLY_LOCATION)
	public URI getApplyLocation() throws URISyntaxException {
		return createStashLocation();
	}

	@PropertyDescription(name = GitConstants.KEY_STASH_DROP_LOCATION)
	public URI getDropLocation() throws URISyntaxException {
		return createStashLocation();
	}

	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		/* extend default commit properties */
		return jsonSerializer.serialize(this, EXTENDED_RESOURCE_SHAPE);
	}
}
