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
import java.util.*;
import java.util.Map.Entry;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.core.users.UserUtilities;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.*;

@ResourceDescription(type = Commit.TYPE)
public class Commit extends GitObject {

	public static final String RESOURCE = "commit"; //$NON-NLS-1$
	public static final String TYPE = "Commit"; //$NON-NLS-1$

	protected static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				new Property(ProtocolConstants.KEY_CONTENT_LOCATION), //
				new Property(GitConstants.KEY_DIFF), //
				new Property(ProtocolConstants.KEY_NAME), //
				new Property(GitConstants.KEY_AUTHOR_NAME), //
				new Property(GitConstants.KEY_AUTHOR_EMAIL), //
				new Property(GitConstants.KEY_AUTHOR_IMAGE), //
				new Property(GitConstants.KEY_COMMITTER_NAME), //
				new Property(GitConstants.KEY_COMMITTER_EMAIL), //
				new Property(GitConstants.KEY_COMMIT_TIME), //
				new Property(GitConstants.KEY_COMMIT_MESSAGE), //
				new Property(GitConstants.KEY_TAGS), //
				new Property(GitConstants.KEY_BRANCHES), //
				new Property(ProtocolConstants.KEY_PARENTS), //
				new Property(GitConstants.KEY_TREE), //
				new Property(GitConstants.KEY_COMMIT_DIFFS)};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	protected RevCommit revCommit;
	protected String pattern;
	protected TreeFilter filter;
	/**
	 * Whether this is a commit at the root of the repository, or only a
	 * particular path (git commit -o {path}).
	 */
	protected boolean isRoot = true;
	protected Map<ObjectId, JSONArray> commitToBranchMap;
	protected Map<ObjectId, Map<String, Ref>> commitToTagMap;

	public Commit(URI cloneLocation, Repository db, RevCommit revCommit, String pattern) {
		super(cloneLocation, db);
		this.revCommit = revCommit;
		this.pattern = pattern;
		if (pattern != null && !pattern.isEmpty()) {
			filter = AndTreeFilter.create(PathFilterGroup.createFromStrings(Collections.singleton(pattern)), TreeFilter.ANY_DIFF);
			isRoot = false;
		}
	}

	public void setCommitToBranchMap(Map<ObjectId, JSONArray> map) {
		this.commitToBranchMap = map;
	}

	public void setCommitToTagMap(Map<ObjectId, Map<String, Ref>> map) {
		this.commitToTagMap = map;
	}

	public Map<ObjectId, JSONArray> getCommitToBranchMap() throws GitAPIException, JSONException, URISyntaxException, IOException, CoreException {
		if (commitToBranchMap == null)
			commitToBranchMap = Log.getCommitToBranchMap(cloneLocation, db);
		return commitToBranchMap;
	}

	public Map<ObjectId, Map<String, Ref>> getCommitToTagMap() throws GitAPIException, JSONException, URISyntaxException, IOException, CoreException {
		if (commitToTagMap == null)
			commitToTagMap = Log.getCommitToTagMap(cloneLocation, db);
		return commitToTagMap;
	}

	/**
	 * Return body of the commit
	 *
	 * @return body of the commit as an Object Stream
	 * @throws IOException when reading the object failed
	 */
	public ObjectStream toObjectStream() throws IOException {
		final TreeWalk w = TreeWalk.forPath(db, pattern, revCommit.getTree());
		if (w == null) {
			return null;
		}
		ObjectId blobId = w.getObjectId(0);
		return db.open(blobId, Constants.OBJ_BLOB).openStream();
	}

	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_CONTENT_LOCATION)
	protected URI getContentLocation() throws URISyntaxException {
		if (!isRoot) // linking to body makes only sense for files
			return BaseToCommitConverter.getCommitLocation(cloneLocation, revCommit.getName(), pattern, BaseToCommitConverter.REMOVE_FIRST_2.setQuery("parts=body")); //$NON-NLS-1$		
		return null; // in the eventuality of null, the property won't be added
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_DIFF)
	protected URI getDiffLocation() throws URISyntaxException {
		return createDiffLocation(revCommit.getName(), null, pattern);
	}

	@PropertyDescription(name = GitConstants.KEY_TREE)
	private URI getTreeLocation() throws URISyntaxException {
		return createTreeLocation(null);
	}

	@PropertyDescription(name = ProtocolConstants.KEY_NAME)
	protected String getName() {
		return revCommit.getName();
	}

	@PropertyDescription(name = GitConstants.KEY_AUTHOR_NAME)
	protected String getAuthorName() {
		PersonIdent author = revCommit.getAuthorIdent();
		return author.getName();
	}

	@PropertyDescription(name = GitConstants.KEY_AUTHOR_EMAIL)
	protected String getAuthorEmail() {
		PersonIdent author = revCommit.getAuthorIdent();
		return author.getEmailAddress();
	}

	@PropertyDescription(name = GitConstants.KEY_AUTHOR_IMAGE)
	protected String getAuthorImage() {
		PersonIdent author = revCommit.getAuthorIdent();
		return UserUtilities.getImageLink(author.getEmailAddress()); // can be null
	}

	@PropertyDescription(name = GitConstants.KEY_COMMITTER_NAME)
	protected String getCommitterName() {
		PersonIdent committer = revCommit.getCommitterIdent();
		return committer.getName();
	}

	@PropertyDescription(name = GitConstants.KEY_COMMITTER_EMAIL)
	protected String getCommitterEmail() {
		PersonIdent committer = revCommit.getCommitterIdent();
		return committer.getEmailAddress();
	}

	@PropertyDescription(name = GitConstants.KEY_COMMIT_TIME)
	protected long getCommitTime() {
		return ((long) revCommit.getCommitTime()) * 1000 /* time in milliseconds */;
	}

	@PropertyDescription(name = GitConstants.KEY_COMMIT_MESSAGE)
	protected String getCommitMessiage() {
		return revCommit.getFullMessage();
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_TAGS)
	protected JSONArray getTags() throws MissingObjectException, JSONException, URISyntaxException, CoreException, IOException, GitAPIException {
		return toJSON(getTagsForCommit());
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_BRANCHES)
	protected JSONArray getBranches() throws JSONException, GitAPIException, URISyntaxException, IOException, CoreException {
		return getCommitToBranchMap().get(revCommit.getId());
	}

	// TODO: expandable?
	@PropertyDescription(name = ProtocolConstants.KEY_PARENTS)
	protected JSONArray getParents() throws JSONException, URISyntaxException, IOException, CoreException {
		return parentsToJSON(revCommit.getParents());
	}

	// TODO: expandable
	@PropertyDescription(name = GitConstants.KEY_COMMIT_DIFFS)
	protected JSONArray getDiffs() throws JSONException, URISyntaxException, MissingObjectException, IncorrectObjectTypeException, IOException {
		if (revCommit.getParentCount() > 0) {
			JSONArray diffs = new JSONArray();

			final TreeWalk tw = new TreeWalk(db);
			final RevWalk rw = new RevWalk(db);
			RevCommit parent = rw.parseCommit(revCommit.getParent(0));
			tw.reset(parent.getTree(), revCommit.getTree());
			tw.setRecursive(true);

			if (filter != null)
				tw.setFilter(filter);
			else
				tw.setFilter(TreeFilter.ANY_DIFF);

			List<DiffEntry> l = DiffEntry.scan(tw);
			for (DiffEntry entr : l) {
				JSONObject diff = new JSONObject();
				diff.put(ProtocolConstants.KEY_TYPE, org.eclipse.orion.server.git.objects.Diff.TYPE);
				diff.put(GitConstants.KEY_COMMIT_DIFF_NEWPATH, entr.getNewPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_OLDPATH, entr.getOldPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_CHANGETYPE, entr.getChangeType().toString());

				// add diff location for the commit
				String path = entr.getChangeType() != ChangeType.DELETE ? entr.getNewPath() : entr.getOldPath();
				diff.put(GitConstants.KEY_DIFF, createDiffLocation(revCommit.getName(), revCommit.getParent(0).getName(), path));
				diff.put(ProtocolConstants.KEY_CONTENT_LOCATION, createContentLocation(entr, path));

				diffs.put(diff);
			}
			tw.release();

			return diffs;
		}
		return null;
	}

	protected JSONArray toJSON(Map<String, Ref> revTags) throws JSONException, URISyntaxException, CoreException, IOException {
		JSONArray children = new JSONArray();
		for (Entry<String, Ref> revTag : revTags.entrySet()) {
			Tag tag = new Tag(cloneLocation, db, revTag.getValue());
			children.put(tag.toJSON());
		}
		return children;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return BaseToCommitConverter.getCommitLocation(cloneLocation, revCommit.getName(), pattern, BaseToCommitConverter.REMOVE_FIRST_2);
	}

	protected Map<String, Ref> getTagsForCommit() throws MissingObjectException, IOException, GitAPIException, JSONException, URISyntaxException, CoreException {
		Map<String, Ref> tags = getCommitToTagMap().get(revCommit.getId());
		if (tags == null)
			tags = new HashMap<String, Ref>();
		return tags;
	}

	protected URI createDiffLocation(String toRefId, String fromRefId, String path) throws URISyntaxException {
		IPath diffPath = new Path(GitServlet.GIT_URI).append(Diff.RESOURCE);

		//diff range format is [fromRef..]toRef
		String diffRange = ""; //$NON-NLS-1$
		if (fromRefId != null)
			diffRange = fromRefId + ".."; //$NON-NLS-1$
		diffRange += toRefId;
		diffPath = diffPath.append(diffRange);

		//clone location is of the form /gitapi/clone/file/{workspaceId}/{projectName}[/{path}]
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);
		if (path == null) {
			diffPath = diffPath.append(clonePath);
		} else if (isRoot) {
			diffPath = diffPath.append(clonePath).append(path);
		} else {
			//need to start from the project root
			//project path is of the form /file/{workspaceId}/{projectName}
			IPath projectRoot = clonePath.uptoSegment(3);
			diffPath = diffPath.append(projectRoot).append(path);
		}

		return new URI(cloneLocation.getScheme(), cloneLocation.getAuthority(), diffPath.toString(), null, null);
	}

	protected URI createTreeLocation(String path) throws URISyntaxException {
		//remove /gitapi/clone from the start of path
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);

		IPath result = new Path(GitServlet.GIT_URI).append(Tree.RESOURCE).append(this.getName()).append(clonePath);
		if (path != null) {
			result.append(path);
		}
		return new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), result.makeAbsolute().toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
	}

	protected URI createContentLocation(final DiffEntry entr, String path) throws URISyntaxException {
		//remove /gitapi/clone from the start of path
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);
		IPath result;
		if (path == null) {
			result = clonePath;
		} else if (isRoot) {
			result = clonePath.append(path);
		} else {
			//need to start from the project root
			//project path is of the form /file/{workspaceId}/{projectName}
			result = clonePath.uptoSegment(3).append(path);
		}
		return new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), result.makeAbsolute().toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
	}

	protected JSONArray parentsToJSON(RevCommit[] revCommits) throws JSONException, IOException, URISyntaxException {
		JSONArray parents = new JSONArray();
		for (RevCommit revCommit : revCommits) {
			JSONObject parent = new JSONObject();
			parent.put(ProtocolConstants.KEY_NAME, revCommit.getName());
			parent.put(ProtocolConstants.KEY_LOCATION, BaseToCommitConverter.getCommitLocation(cloneLocation, revCommit.getName(), pattern, BaseToCommitConverter.REMOVE_FIRST_2));
			parents.put(parent);
		}
		return parents;
	}
}
