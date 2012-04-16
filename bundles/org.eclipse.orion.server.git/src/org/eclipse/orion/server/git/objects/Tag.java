/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
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

	private enum TagType {
		LIGHTWEIGHT, ANNOTATED;

		public static TagType valueOf(Tag t) {
			if (t.tag != null)
				return ANNOTATED;
			else if (t.ref != null)
				return LIGHTWEIGHT;
			throw new IllegalArgumentException("Illegal tag type: " + t);
		}
	}

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

	public Tag(URI cloneLocation, Repository db, Ref ref) throws IOException, CoreException {
		super(cloneLocation, db);

		RevWalk rw = new RevWalk(db);
		RevObject any;
		try {
			any = rw.parseAny(db.resolve(ref.getName()));
		} finally {
			rw.dispose();
		}
		if (any instanceof RevTag)
			this.tag = (RevTag) any;
		else
			this.ref = ref;
	}

	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject result = super.toJSON();
		result.put(ProtocolConstants.KEY_NAME, getName(false));
		result.put(GitConstants.KEY_COMMIT, getCommitLocation());
		result.put(ProtocolConstants.KEY_LOCAL_TIMESTAMP, (long) getTime() * 1000);
		result.put(GitConstants.KEY_TAG_TYPE, TagType.valueOf(this));
		result.put(ProtocolConstants.KEY_FULL_NAME, getName(true));
		return result;
	}

	public JSONObject toJSON(JSONObject log) throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject tagJSON = this.toJSON();
		tagJSON.put(GitConstants.KEY_TAG_COMMIT, log);
		return tagJSON;
	}

	private String getName(boolean fullName) {
		if (tag != null)
			return fullName ? Constants.R_TAGS + tag.getTagName() : tag.getTagName();
		if (ref != null)
			return fullName ? ref.getName() : Repository.shortenRefName(ref.getName());
		return null;
	}

	protected URI getLocation() throws URISyntaxException {
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

	private int getTime() {
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

	public String getRevCommitName() {
		return parseCommit().getName();
	}

	@Override
	protected String getType() {
		return TYPE;
	}
}