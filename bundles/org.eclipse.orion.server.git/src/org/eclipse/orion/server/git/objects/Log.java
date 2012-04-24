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
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.json.*;

/**
 * A compound object for {@link Commit}s.
 *
 */
public class Log extends GitObject {

	private Iterable<RevCommit> commits;
	private String pattern;
	private Ref toRefId;
	private Ref fromRefId;

	public Log(URI cloneLocation, Repository db, Iterable<RevCommit> commits, String pattern, Ref toRefId, Ref fromRefId) {
		super(cloneLocation, db);
		this.commits = commits;
		this.pattern = pattern;
		this.toRefId = toRefId;
		this.fromRefId = fromRefId;
	}

	public void setCommits(Iterable<RevCommit> commits) {
		this.commits = commits;
	}

	public JSONObject toJSON(int page, int pageSize) throws JSONException, URISyntaxException, IOException, CoreException {
		Assert.isNotNull(commits, "'commits' is null");
		Map<ObjectId, JSONArray> commitToBranchMap = getCommitToBranchMap(cloneLocation, db);

		JSONObject result = super.toJSON();
		JSONArray children = new JSONArray();
		int i = 0;
		Iterator<RevCommit> iterator = commits.iterator();
		while (iterator.hasNext()) {
			RevCommit revCommit = (RevCommit) iterator.next();
			Commit commit = new Commit(cloneLocation, db, revCommit, pattern);
			commit.setCommitToBranchMap(commitToBranchMap);
			children.put(commit.toJSON());
			if (i++ == pageSize - 1)
				break;
		}
		boolean hasNextPage = iterator.hasNext();

		result.put(ProtocolConstants.KEY_CHILDREN, children);
		result.put(GitConstants.KEY_REPOSITORY_PATH, pattern == null ? "" : pattern); //$NON-NLS-1$

		if (toRefId != null) {
			String refTargetName = toRefId.getTarget().getName();
			if (refTargetName.startsWith(Constants.R_HEADS)) {
				// this is a branch
				result.put(GitConstants.KEY_LOG_TO_REF, new Branch(cloneLocation, db, toRefId.getTarget()).toJSON());
			}
		}
		if (fromRefId != null) {
			String refTargetName = fromRefId.getTarget().getName();
			if (refTargetName.startsWith(Constants.R_HEADS)) {
				// this is a branch
				result.put(GitConstants.KEY_LOG_FROM_REF, new Branch(cloneLocation, db, fromRefId.getTarget()).toJSON());
			}
		}

		if (page > 0) {
			StringBuilder c = new StringBuilder(""); //$NON-NLS-1$
			if (fromRefId != null)
				c.append(fromRefId.getName());
			if (fromRefId != null && toRefId != null)
				c.append(".."); //$NON-NLS-1$
			if (toRefId != null)
				c.append(Repository.shortenRefName(toRefId.getName()));
			final String q = "page=%d&pageSize=%d"; //$NON-NLS-1$
			if (page > 1) {
				result.put(ProtocolConstants.KEY_PREVIOUS_LOCATION, BaseToCommitConverter.getCommitLocation(cloneLocation, c.toString(), pattern, BaseToCommitConverter.REMOVE_FIRST_2.setQuery(String.format(q, page - 1, pageSize))));
			}
			if (hasNextPage) {
				result.put(ProtocolConstants.KEY_NEXT_LOCATION, BaseToCommitConverter.getCommitLocation(cloneLocation, c.toString(), pattern, BaseToCommitConverter.REMOVE_FIRST_2.setQuery(String.format(q, page + 1, pageSize))));
			}
		}

		return result;
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		StringBuilder c = new StringBuilder();
		if (fromRefId != null)
			c.append(fromRefId.getName());
		if (fromRefId != null && toRefId != null)
			c.append(".."); //$NON-NLS-1$
		if (toRefId != null)
			c.append(Repository.shortenRefName(toRefId.getName()));
		// TODO: lost paging info
		return BaseToCommitConverter.getCommitLocation(cloneLocation, c.toString(), pattern, BaseToCommitConverter.REMOVE_FIRST_2);
	}

	static Map<ObjectId, JSONArray> getCommitToBranchMap(URI cloneLocation, Repository db) throws JSONException, URISyntaxException, IOException, CoreException {
		HashMap<ObjectId, JSONArray> commitToBranch = new HashMap<ObjectId, JSONArray>();
		Git git = new Git(db);
		List<Ref> branchRefs = git.branchList().setListMode(ListMode.ALL).call();
		for (Ref branchRef : branchRefs) {
			ObjectId commitId = branchRef.getLeaf().getObjectId();
			Branch branch = new Branch(cloneLocation, db, branchRef);
			JSONArray branchesArray = commitToBranch.get(commitId);
			if (branchesArray != null) {
				branchesArray.put(branch.toJSON());
			} else {
				branchesArray = new JSONArray();
				branchesArray.put(branch.toJSON());
				commitToBranch.put(commitId, branchesArray);
			}
		}
		return commitToBranch;
	}

	@Override
	protected String getType() {
		return Commit.TYPE;
	}
}
