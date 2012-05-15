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
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToCommitConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * A compound object for {@link Commit}s.
 *
 */
@ResourceDescription(type = "Commit")
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

		if (toRefId != null)
			result.put(GitConstants.KEY_LOG_TO_REF, createJSONObjectForRef(toRefId.getTarget()));
		if (fromRefId != null)
			result.put(GitConstants.KEY_LOG_FROM_REF, createJSONObjectForRef(fromRefId.getTarget()));

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

	private JSONObject createJSONObjectForRef(Ref targetRef) throws JSONException, URISyntaxException, IOException, CoreException {
		JSONObject result = null;
		String name = targetRef.getName();
		if (name.startsWith(Constants.R_HEADS)) {
			result = new Branch(cloneLocation, db, targetRef).toJSON();
		} else if (name.startsWith(Constants.R_REMOTES)) {
			Remote remote = findRemote(name);
			String remoteBranchName = computeRemoteBranchName(name, remote);
			result = new RemoteBranch(cloneLocation, db, remote, remoteBranchName).toJSON();
		}
		Assert.isNotNull(result, NLS.bind("Unexpected target Ref: {0}", name));
		return result;
	}

	private Remote findRemote(String refName) throws URISyntaxException {
		Assert.isLegal(refName.startsWith(Constants.R_REMOTES), NLS.bind("Expected Ref starting with {0} was {1}", Constants.R_REMOTES, refName));
		IPath remoteNameCandidate = new Path(refName).removeFirstSegments(2);
		List<RemoteConfig> remoteConfigs = RemoteConfig.getAllRemoteConfigs(getConfig());
		for (int i = 1; i < remoteNameCandidate.segmentCount(); i++) {
			for (RemoteConfig remoteConfig : remoteConfigs) {
				IPath uptoSegment = remoteNameCandidate.uptoSegment(i);
				if (remoteConfig.getName().equals(uptoSegment.toString()))
					return new Remote(cloneLocation, db, remoteConfig.getName());
			}
		}
		Assert.isTrue(false, NLS.bind("Could not find Remote for {0}", refName));
		return null;
	}

	private String computeRemoteBranchName(String targetRefName, Remote remote) {
		String prefix = Constants.R_REMOTES + remote.getName() + "/"; //$NON-NLS-1$
		return targetRefName.substring(prefix.length());
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		StringBuilder c = new StringBuilder();
		if (fromRefId != null)
			c.append(fromRefId.getName());
		if (fromRefId != null && toRefId != null)
			c.append(".."); //$NON-NLS-1$
		if (toRefId != null)
			c.append(toRefId.getName());
		// TODO: lost paging info
		return BaseToCommitConverter.getCommitLocation(cloneLocation, GitUtils.encode(c.toString()), pattern, BaseToCommitConverter.REMOVE_FIRST_2);
	}

	static Map<ObjectId, JSONArray> getCommitToBranchMap(URI cloneLocation, Repository db) throws JSONException, URISyntaxException, IOException, CoreException {
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

	@Override
	protected String getType() {
		return Commit.TYPE;
	}
}
