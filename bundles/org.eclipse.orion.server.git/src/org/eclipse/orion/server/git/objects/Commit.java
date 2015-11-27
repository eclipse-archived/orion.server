/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.core.users.UserUtilities;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
				new Property(GitConstants.KEY_COMMIT_DIFFS) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	protected RevCommit revCommit;
	protected String pattern;
	protected TreeFilter filter;
	/**
	 * Whether this is a commit at the root of the repository, or only a particular path (git commit -o {path}).
	 */
	protected boolean isRoot = true;
	protected Map<ObjectId, JSONArray> commitToBranchMap;
	protected Map<ObjectId, Map<String, Ref>> commitToTagMap;

	public Commit(URI cloneLocation, Repository db, RevCommit revCommit, String pattern) {
		super(cloneLocation, db);
		this.revCommit = revCommit;
		if (revCommit.getParentCount() == 0) {
			this.revCommit = parseCommit(revCommit);
		}
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
	 * @throws IOException
	 *             when reading the object failed
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
			return BaseToCommitConverter.getCommitLocation(cloneLocation, revCommit.getName(), pattern,
					BaseToCommitConverter.REMOVE_FIRST_2.setQuery("parts=body")); //$NON-NLS-1$		
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
	protected JSONObject getDiffs() throws JSONException, URISyntaxException, MissingObjectException, IncorrectObjectTypeException, IOException {
		JSONArray diffs = new JSONArray();
		JSONObject result = new JSONObject();
		TreeWalk tw = null;
		try {
			tw = new TreeWalk(db);
			tw.setRecursive(true);
			List<DiffEntry> l = null;
			String fromName = null;
			if (revCommit.getParentCount() > 0) {
				RevCommit parent = parseCommit(revCommit.getParent(0));
				tw.reset(parent.getTree(), revCommit.getTree());
				if (filter != null)
					tw.setFilter(filter);
				else
					tw.setFilter(TreeFilter.ANY_DIFF);

				l = DiffEntry.scan(tw);
				fromName = revCommit.getParent(0).getName();
			} else {
				RevWalk rw = null;
				DiffFormatter diffFormat = null;
				try {
					rw = new RevWalk(db);
					diffFormat = new DiffFormatter(NullOutputStream.INSTANCE);
					diffFormat.setRepository(db);
					if (filter != null)
						diffFormat.setPathFilter(filter);
					l = diffFormat.scan(new EmptyTreeIterator(), new CanonicalTreeParser(null, rw.getObjectReader(), revCommit.getTree()));
				} finally {
					diffFormat.close();
					rw.close();
				}
			}

			int pageSize = 100;
			int page = 1;
			int start = pageSize * (page - 1);
			int end = Math.min(pageSize + start, l.size());
			int i = start;
			for (i = start; i < end; i++) {
				DiffEntry entr = l.get(i);
				JSONObject diff = new JSONObject();
				diff.put(ProtocolConstants.KEY_TYPE, org.eclipse.orion.server.git.objects.Diff.TYPE);
				diff.put(GitConstants.KEY_COMMIT_DIFF_NEWPATH, entr.getNewPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_OLDPATH, entr.getOldPath());
				diff.put(GitConstants.KEY_COMMIT_DIFF_CHANGETYPE, entr.getChangeType().toString());

				// add diff location for the commit
				String path = entr.getChangeType() != ChangeType.DELETE ? entr.getNewPath() : entr.getOldPath();
				diff.put(GitConstants.KEY_DIFF, createDiffLocation(revCommit.getName(), fromName, path));
				diff.put(ProtocolConstants.KEY_CONTENT_LOCATION, createContentLocation(entr, path));
				diff.put(GitConstants.KEY_TREE, createTreeLocation(path));

				diffs.put(diff);
			}

			result.put(ProtocolConstants.KEY_TYPE, org.eclipse.orion.server.git.objects.Diff.TYPE);
			result.put(ProtocolConstants.KEY_CHILDREN, diffs);
			result.put(ProtocolConstants.KEY_LENGTH, l.size());
			if (i < l.size()) {
				URI diffLocation = createDiffLocation(revCommit.getName(), fromName, "");
				URI nextLocation = new URI(diffLocation.getScheme(), diffLocation.getUserInfo(), diffLocation.getHost(), diffLocation.getPort(),
						diffLocation.getPath(), "pageSize=" + pageSize + "&page=" + (page + 1), diffLocation.getFragment());
				result.put(ProtocolConstants.KEY_NEXT_LOCATION, nextLocation);
			}
		} finally {
			tw.close();
		}
		return result;
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

		// diff range format is [fromRef..]toRef
		String diffRange = ""; //$NON-NLS-1$
		if (fromRefId != null)
			diffRange = fromRefId + ".."; //$NON-NLS-1$
		diffRange += toRefId;
		diffPath = diffPath.append(diffRange);

		// clone location is of the form /gitapi/clone/file/{workspaceId}/{projectName}[/{path}]
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);
		if (path == null) {
			diffPath = diffPath.append(clonePath);
		} else if (isRoot) {
			diffPath = diffPath.append(clonePath).append(path);
		} else {
			// need to start from the project root
			// project path is of the form /file/{workspaceId}/{projectName}
			IPath projectRoot = clonePath.uptoSegment(3);
			diffPath = diffPath.append(projectRoot).append(path);
		}

		return new URI(cloneLocation.getScheme(), cloneLocation.getAuthority(), diffPath.toString(), null, null);
	}

	protected URI createTreeLocation(String path) throws URISyntaxException {
		// remove /gitapi/clone from the start of path
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);

		IPath result = new Path(GitServlet.GIT_URI).append(Tree.RESOURCE).append(clonePath).append(this.getName());
		if (path != null) {
			result = result.append(path);
		}
		return new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), result.makeAbsolute()
				.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
	}

	protected URI createContentLocation(final DiffEntry entr, String path) throws URISyntaxException {
		// remove /gitapi/clone from the start of path
		IPath clonePath = new Path(cloneLocation.getPath()).removeFirstSegments(2);
		IPath result;
		if (path == null) {
			result = clonePath;
		} else if (isRoot) {
			result = clonePath.append(path);
		} else {
			// need to start from the project root
			// project path is of the form /file/{workspaceId}/{projectName}
			result = clonePath.uptoSegment(3).append(path);
		}
		return new URI(cloneLocation.getScheme(), cloneLocation.getUserInfo(), cloneLocation.getHost(), cloneLocation.getPort(), result.makeAbsolute()
				.toString(), cloneLocation.getQuery(), cloneLocation.getFragment());
	}

	protected JSONArray parentsToJSON(RevCommit[] revCommits) throws JSONException, IOException, URISyntaxException {
		JSONArray parents = new JSONArray();
		for (RevCommit revCommit : revCommits) {
			JSONObject parent = new JSONObject();
			parent.put(ProtocolConstants.KEY_NAME, revCommit.getName());
			parent.put(ProtocolConstants.KEY_LOCATION,
					BaseToCommitConverter.getCommitLocation(cloneLocation, revCommit.getName(), pattern, BaseToCommitConverter.REMOVE_FIRST_2));
			parents.put(parent);
		}
		return parents;
	}

	private RevCommit parseCommit(RevCommit revCommit) {
		RevWalk rw = null;
		try {
			rw = new RevWalk(db);
			return rw.parseCommit(revCommit);
		} catch (IOException e) {
			return revCommit;
		} finally {
			rw.close();
		}
	}
}
