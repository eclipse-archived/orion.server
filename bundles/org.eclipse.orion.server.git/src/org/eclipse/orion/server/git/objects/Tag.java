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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.JSONException;
import org.json.JSONObject;

public class Tag extends GitObject {

	public static final String RESOURCE = "tag"; //$NON-NLS-1$
	public static final String TYPE = "Tag"; //$NON-NLS-1$
	public static final Comparator<Tag> COMPARATOR = new Comparator<Tag>() {
		public int compare(Tag o1, Tag o2) {
			return o1.getTime() < o2.getTime() ? 1 : (o1.getTime() > o2.getTime() ? -1 : o2.getName(false).compareTo(o1.getName(false)));
		}
	};

	private RevTag tag;
	private Ref ref;
	private URI tagLocation;
	private URI commitLocation;

	public Tag(URI cloneLocation, Repository db, RevTag tag) throws URISyntaxException, CoreException {
		super(cloneLocation, db);
		this.tag = tag;
	}

	// TODO: bug 356943 - revert when bug 360650 is fixed
	public Tag(URI cloneLocation, Repository db, Ref ref) throws URISyntaxException, CoreException {
		super(cloneLocation, db);
		this.ref = ref;
	}

	public JSONObject toJSON() throws JSONException, URISyntaxException {
		JSONObject result = new JSONObject();
		result.put(ProtocolConstants.KEY_NAME, getName(false));
		result.put(ProtocolConstants.KEY_LOCATION, getLocation());
		result.put(GitConstants.KEY_COMMIT, getCommitLocation());
		result.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, (long) getTime() * 1000);
		result.put(ProtocolConstants.KEY_TYPE, TYPE);
		result.put(ProtocolConstants.KEY_FULL_NAME, getName(true));
		return result;
	}

	private String getName(boolean fullName) {
		if (tag != null)
			return tag.getTagName();
		if (ref != null)
			return fullName ? ref.getName() : Repository.shortenRefName(ref.getName());
		return null;
	}

	private URI getLocation() throws URISyntaxException {
		if (tagLocation == null) {
			IPath p = new Path(cloneLocation.getPath());
			p = p.uptoSegment(1).append(RESOURCE).append(getName(false)).addTrailingSeparator().append(p.removeFirstSegments(2));
			tagLocation = new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), p.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
		}
		return tagLocation;
	}

	private URI getCommitLocation() throws URISyntaxException {
		if (commitLocation == null) {
			IPath p = new Path(cloneLocation.getPath());
			p = p.uptoSegment(1).append(Commit.RESOURCE).append(parseCommit().getName()).addTrailingSeparator().append(p.removeFirstSegments(2));
			commitLocation = new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), p.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
		}
		return commitLocation;
	}

	public int getTime() {
		RevCommit c = parseCommit();
		if (c != null)
			return c.getCommitTime();
		return 0;
	}

	private ObjectId getObjectId() {
		if (tag != null)
			return tag.toObjectId();
		else if (ref != null)
			return ref.getObjectId();
		else
			return null;
	}

	private RevCommit parseCommit() {
		ObjectId oid = getObjectId();
		if (oid == null)
			return null;
		RevWalk walk = new RevWalk(db);
		try {
			return walk.parseCommit(oid);
		} catch (IOException e) {
			// ignore and return null
		} finally {
			walk.release();
		}
		return null;
	}
}