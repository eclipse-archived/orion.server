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
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.*;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.users.UserUtilities;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.*;

public class Commit extends GitObject {

	public static final String RESOURCE = "commit"; //$NON-NLS-1$
	public static final String TYPE = "Commit"; //$NON-NLS-1$

	private RevCommit revCommit;
	private String pattern;
	private TreeFilter filter;
	private boolean isRoot = true;
	private Map<ObjectId, JSONArray> commitToBranchMap;

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

	public Map<ObjectId, JSONArray> getCommitToBranchMap() throws JSONException {
		if (commitToBranchMap == null)
			commitToBranchMap = Log.getCommitToBranchMap(db);
		return commitToBranchMap;
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

	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject commit = new JSONObject();
		commit.put(ProtocolConstants.KEY_LOCATION, BaseToCommitConverter.getCommitLocation(cloneLocation, revCommit.getName(), pattern, BaseToCommitConverter.REMOVE_FIRST_2));
		if (!isRoot) // linking to body makes only sense for files
			commit.put(ProtocolConstants.KEY_CONTENT_LOCATION, BaseToCommitConverter.getCommitLocation(cloneLocation, revCommit.getName(), pattern, BaseToCommitConverter.REMOVE_FIRST_2.setQuery("parts=body"))); //$NON-NLS-1$
		commit.put(GitConstants.KEY_DIFF, createDiffLocation(revCommit.getName(), null, pattern, isRoot));
		commit.put(ProtocolConstants.KEY_NAME, revCommit.getName());
		PersonIdent author = revCommit.getAuthorIdent();
		commit.put(GitConstants.KEY_AUTHOR_NAME, author.getName());
		commit.put(GitConstants.KEY_AUTHOR_EMAIL, author.getEmailAddress());
		String authorImage = UserUtilities.getImageLink(author.getEmailAddress());
		if (authorImage != null)
			commit.put(GitConstants.KEY_AUTHOR_IMAGE, authorImage);
		PersonIdent committer = revCommit.getCommitterIdent();
		commit.put(GitConstants.KEY_COMMITTER_NAME, committer.getName());
		commit.put(GitConstants.KEY_COMMITTER_EMAIL, committer.getEmailAddress());
		commit.put(GitConstants.KEY_COMMIT_TIME, ((long) revCommit.getCommitTime()) * 1000 /* time in milliseconds */);
		commit.put(GitConstants.KEY_COMMIT_MESSAGE, revCommit.getFullMessage());
		commit.put(GitConstants.KEY_TAGS, toJSON(getTagsForCommit()));
		commit.put(ProtocolConstants.KEY_TYPE, TYPE);
		commit.put(GitConstants.KEY_BRANCHES, getCommitToBranchMap().get(revCommit.getId()));
		commit.put(ProtocolConstants.KEY_PARENTS, parentsToJSON(revCommit.getParents()));

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
				diff.put(GitConstants.KEY_DIFF, createDiffLocation(revCommit.getName(), revCommit.getParent(0).getName(), path, isRoot));

				diffs.put(diff);
			}
			tw.release();

			commit.put(GitConstants.KEY_COMMIT_DIFFS, diffs);
		}

		return commit;
	}

	private JSONArray toJSON(Map<String, Ref> revTags) throws JSONException, URISyntaxException, CoreException, IOException {
		JSONArray children = new JSONArray();
		for (Entry<String, Ref> revTag : revTags.entrySet()) {
			Tag tag = new Tag(cloneLocation, db, revTag.getValue());
			children.put(tag.toJSON());
		}
		return children;
	}

	// from https://gist.github.com/839693, credits to zx
	private Map<String, Ref> getTagsForCommit() throws MissingObjectException, IOException {
		final Map<String, Ref> revTags = new HashMap<String, Ref>();
		final RevWalk walk = new RevWalk(db);
		try {
			walk.reset();
			for (final Entry<String, Ref> revTag : db.getTags().entrySet()) {
				final RevObject obj = walk.parseAny(revTag.getValue().getObjectId());
				final RevCommit tagCommit;
				if (obj instanceof RevCommit) {
					tagCommit = (RevCommit) obj;
				} else if (obj instanceof RevTag) {
					tagCommit = walk.parseCommit(((RevTag) obj).getObject());
				} else {
					continue;
				}
				if (revCommit.equals(tagCommit) || walk.isMergedInto(revCommit, tagCommit)) {
					revTags.put(revTag.getKey(), revTag.getValue());
				}
			}
		} finally {
			walk.dispose();
		}
		return revTags;
	}

	private URI createDiffLocation(String toRefId, String fromRefId, String path, boolean isRoot) throws URISyntaxException {
		// TODO: use IPath, not String
		String diffPath = GitServlet.GIT_URI + "/" + Diff.RESOURCE + "/"; //$NON-NLS-1$ //$NON-NLS-2$

		if (fromRefId != null)
			diffPath += fromRefId + ".."; //$NON-NLS-1$

		diffPath += toRefId + "/"; //$NON-NLS-1$

		if (path == null) {
			diffPath += new Path(cloneLocation.getPath()).removeFirstSegments(2);
		} else if (isRoot) {
			diffPath += new Path(cloneLocation.getPath()).removeFirstSegments(2).append(path);
		} else {
			IPath p = new Path(cloneLocation.getPath());
			// TODO: not sure if this is right, but it's fine with tests
			diffPath += p.removeLastSegments(p.segmentCount() - 4).removeFirstSegments(2).append(path);
		}

		return new URI(cloneLocation.getScheme(), cloneLocation.getAuthority(), diffPath, null, null);
	}

	private JSONArray parentsToJSON(RevCommit[] revCommits) throws JSONException, IOException, URISyntaxException {
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
