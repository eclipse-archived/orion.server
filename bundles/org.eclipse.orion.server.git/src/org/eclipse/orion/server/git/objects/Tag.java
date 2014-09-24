/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Tag.TYPE)
public class Tag extends GitObject {

	private enum TagType {
		LIGHTWEIGHT, ANNOTATED;

		public static TagType valueOf(Tag t) {
			t.parseTag();
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
		@Override
		public int compare(Tag o1, Tag o2) {
			return o1.getTime() < o2.getTime() ? 1 : (o1.getTime() > o2.getTime() ? -1 : o2.getName(false, false).compareTo(o1.getName(false, false)));
		}
	};

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				new Property(ProtocolConstants.KEY_NAME), //
				new Property(GitConstants.KEY_COMMIT), //
				new Property(GitConstants.KEY_TREE), //
				new Property(ProtocolConstants.KEY_LOCAL_TIMESTAMP), //
				new Property(GitConstants.KEY_TAG_TYPE), //
				new Property(ProtocolConstants.KEY_FULL_NAME) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	private RevTag tag;
	private Ref ref;
	private URI tagLocation;
	private URI commitLocation;
	private RevCommit commit;

	public Tag(URI cloneLocation, Repository db, Ref ref) throws IOException, CoreException {
		super(cloneLocation, db);
		this.ref = ref;
	}

	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_NAME)
	private String getName() {
		return getName(false, false);
	}

	@PropertyDescription(name = GitConstants.KEY_TAG_TYPE)
	private TagType getTagType() {
		return TagType.valueOf(this);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_FULL_NAME)
	private String getFullName() {
		return getName(true, false);
	}

	private RevTag parseTag() {
		parseCommit();
		return this.tag;
	}

	private RevCommit parseCommit() {
		if (this.commit == null) {
			RevWalk rw = new RevWalk(db);
			RevObject any;
			try {
				any = rw.parseAny(this.ref.getObjectId());
				if (any instanceof RevTag) {
					this.tag = (RevTag) any;
					this.commit = (RevCommit) rw.peel(any);
				} else if (any instanceof RevCommit) {
					this.commit = (RevCommit) any;
				}
			} catch (IOException e) {
			} finally {
				rw.dispose();
			}
		}
		return commit;
	}

	public JSONObject toJSON(JSONObject log) throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject tagJSON = this.toJSON();
		tagJSON.put(GitConstants.KEY_TAG_COMMIT, log);
		return tagJSON;
	}

	private String getName(boolean fullName, boolean encode) {
		String name = null;
		if (tag != null)
			name = fullName ? Constants.R_TAGS + tag.getTagName() : tag.getTagName();
		if (ref != null)
			name = fullName ? ref.getName() : Repository.shortenRefName(ref.getName());
		if (name == null)
			return null;
		if (encode)
			name = GitUtils.encode(name);
		return name;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		if (tagLocation == null) {
			IPath p = new Path(cloneLocation.getPath());
			p = p.uptoSegment(1).append(RESOURCE).append(getName(false, true)).addTrailingSeparator().append(p.removeFirstSegments(2));
			tagLocation = new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), p.toString(),
					cloneLocation.getQuery(), cloneLocation.getFragment());
		}
		return tagLocation;
	}

	@PropertyDescription(name = GitConstants.KEY_COMMIT)
	private URI getCommitLocation() throws URISyntaxException {
		if (commitLocation == null) {
			IPath p = new Path(cloneLocation.getPath());
			p = p.uptoSegment(1).append(Commit.RESOURCE).append(parseCommit().getName()).addTrailingSeparator().append(p.removeFirstSegments(2));
			commitLocation = new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), p.toString(),
					cloneLocation.getQuery(), cloneLocation.getFragment());
		}
		return commitLocation;
	}

	@PropertyDescription(name = GitConstants.KEY_TREE)
	private URI getTreeLocation() throws URISyntaxException {
		return createTreeLocation(null);
	}

	private URI createTreeLocation(String path) throws URISyntaxException {
		// remove /gitapi/clone from the start of path
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);

		IPath result = new Path(GitServlet.GIT_URI).append(Tree.RESOURCE).append(clonePath).append(GitUtils.encode(this.getName()));
		if (path != null) {
			result.append(path);
		}
		return new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), result.makeAbsolute()
				.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
	}

	@PropertyDescription(name = ProtocolConstants.KEY_LOCAL_TIMESTAMP)
	private long getTime() {
		RevCommit c = parseCommit();
		if (c != null)
			return (long) c.getCommitTime() * 1000;
		return 0;
	}

	public String getRevCommitName() {
		return parseCommit().getName();
	}
}