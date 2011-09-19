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
import java.util.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.GitConstants;
import org.json.*;

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
		if (commits == null)
			throw new IllegalStateException("'commits' is null");

		Map<ObjectId, JSONArray> commitToBranchMap = getCommitToBranchMap(db);

		JSONObject result = new JSONObject();

		boolean pageable = (page > 0);
		int startIndex = (page - 1) * pageSize;
		int index = 0;

		JSONArray children = new JSONArray();
		for (RevCommit revCommit : commits) {
			if (pageable && index < startIndex) {
				index++;
				continue;
			}

			if (pageable && index >= startIndex + pageSize)
				break;

			index++;

			Commit commit = new Commit(cloneLocation, db, revCommit, pattern);
			commit.setCommitToBranchMap(commitToBranchMap);
			children.put(commit.toJSON());
		}
		result.put(ProtocolConstants.KEY_CHILDREN, children);

		result.put(GitConstants.KEY_REPOSITORY_PATH, pattern == null ? "" : pattern); //$NON-NLS-1$
		result.put(GitConstants.KEY_CLONE, cloneLocation);

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

		return result;
	}

	static Map<ObjectId, JSONArray> getCommitToBranchMap(Repository db) throws JSONException {
		HashMap<ObjectId, JSONArray> commitToBranch = new HashMap<ObjectId, JSONArray>();
		Git git = new Git(db);
		List<Ref> branchRefs = git.branchList().setListMode(ListMode.ALL).call();
		for (Ref branchRef : branchRefs) {
			ObjectId commitId = branchRef.getLeaf().getObjectId();
			JSONObject branch = new JSONObject();
			branch.put(ProtocolConstants.KEY_FULL_NAME, branchRef.getName());

			JSONArray branchesArray = commitToBranch.get(commitId);
			if (branchesArray != null) {
				branchesArray.put(branch);
			} else {
				branchesArray = new JSONArray();
				branchesArray.put(branch);
				commitToBranch.put(commitId, branchesArray);
			}
		}
		return commitToBranch;
	}
}
