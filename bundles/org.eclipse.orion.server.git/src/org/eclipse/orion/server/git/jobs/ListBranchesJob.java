/*******************************************************************************
 * Copyright (c) 2012, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.jobs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Branch;
import org.eclipse.orion.server.git.objects.Log;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Job listing all local branches in the.
 *
 */
public class ListBranchesJob extends GitJob {

	private IPath path;
	private URI cloneLocation;
	private int commitsSize;
	private int pageNo;
	private int pageSize;
	private String baseLocation;
	private String nameFilter;

	/**
	 * Creates job with given page range and adding <code>commitsSize</code> commits to every branch.
	 * 
	 * @param userRunningTask
	 * @param repositoryPath
	 * @param cloneLocation
	 * @param commitsSize
	 *            user 0 to omit adding any log, only CommitLocation will be attached
	 * @param pageNo
	 * @param pageSize
	 *            use negative to indicate that all commits need to be returned
	 * @param baseLocation
	 *            URI used as a base for generating next and previous page links. Should not contain any parameters.
	 * @param nameFilter
	 *            used to filter branches by name
	 */
	public ListBranchesJob(String userRunningTask, IPath repositoryPath, URI cloneLocation, int commitsSize, int pageNo, int pageSize, String baseLocation,
			String nameFilter) {
		super(userRunningTask, false);
		this.path = repositoryPath;
		this.cloneLocation = cloneLocation;
		this.commitsSize = commitsSize;
		this.pageNo = pageNo;
		this.pageSize = pageSize;
		this.baseLocation = baseLocation;
		this.nameFilter = nameFilter;
		setFinalMessage("Branches list generated");
	}

	/**
	 * Creates job returning list of all branches.
	 * 
	 * @param userRunningTask
	 * @param repositoryPath
	 * @param cloneLocation
	 */
	public ListBranchesJob(String userRunningTask, IPath repositoryPath, URI cloneLocation) {
		this(userRunningTask, repositoryPath, cloneLocation, 0, 0, -1, null, null);
	}

	/**
	 * Creates job returning list of all branches adding <code>commitsSize</code> commits to every branch.
	 * 
	 * @param userRunningTask
	 * @param repositoryPath
	 * @param cloneLocation
	 * @param commitsSize
	 */
	public ListBranchesJob(String userRunningTask, IPath repositoryPath, URI cloneLocation, int commitsSize) {
		this(userRunningTask, repositoryPath, cloneLocation, commitsSize, 0, -1, null, null);
	}

	private ObjectId getCommitObjectId(Repository db, ObjectId oid) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		RevWalk walk = new RevWalk(db);
		try {
			return walk.parseCommit(oid);
		} finally {
			walk.close();
		}
	}

	@Override
	protected IStatus performJob() {
		Repository db = null;
		try {
			File gitDir = GitUtils.getGitDir(path);
			db = FileRepositoryBuilder.create(gitDir);
			Git git = new Git(db);
			List<Ref> branchRefs = git.branchList().call();
			List<Branch> branches = new ArrayList<Branch>(branchRefs.size());
			for (Ref ref : branchRefs) {
				if (nameFilter != null && !nameFilter.equals("")) {
					String shortName = Repository.shortenRefName(ref.getName());
					if (shortName.toLowerCase().contains(nameFilter.toLowerCase())) {
						branches.add(new Branch(cloneLocation, db, ref));
					}
				} else {
					branches.add(new Branch(cloneLocation, db, ref));
				}
			}
			Collections.sort(branches, Branch.COMPARATOR);
			JSONObject result = new JSONObject();
			JSONArray children = new JSONArray();
			int firstBranch = pageSize > 0 ? pageSize * (pageNo - 1) : 0;
			int lastBranch = pageSize > 0 ? firstBranch + pageSize - 1 : branches.size() - 1;
			lastBranch = lastBranch > branches.size() - 1 ? branches.size() - 1 : lastBranch;
			if (pageNo > 1 && baseLocation != null) {
				String prev = baseLocation + "?page=" + (pageNo - 1) + "&pageSize=" + pageSize;
				if (nameFilter != null && !nameFilter.equals("")) {
					prev += "&filter=" + GitUtils.encode(nameFilter);
				}
				if (commitsSize > 0) {
					prev += "&" + GitConstants.KEY_TAG_COMMITS + "=" + commitsSize;
				}
				result.put(ProtocolConstants.KEY_PREVIOUS_LOCATION, prev);
			}
			if (lastBranch < branches.size() - 1) {
				String next = baseLocation + "?page=" + (pageNo + 1) + "&pageSize=" + pageSize;
				if (nameFilter != null && !nameFilter.equals("")) {
					next += "&filter=" + GitUtils.encode(nameFilter);
				}
				if (commitsSize > 0) {
					next += "&" + GitConstants.KEY_TAG_COMMITS + "=" + commitsSize;
				}
				result.put(ProtocolConstants.KEY_NEXT_LOCATION, next);
			}
			for (int i = firstBranch; i <= lastBranch; i++) {
				Branch branch = branches.get(i);
				if (commitsSize == 0) {
					children.put(branch.toJSON());
				} else {
					String branchName = branch.getName(true, false);
					ObjectId toObjectId = db.resolve(branchName);
					Ref toRefId = db.getRef(branchName);
					if (toObjectId == null) {
						String msg = NLS.bind("No ref or commit found: {0}", branchName);
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
					}
					toObjectId = getCommitObjectId(db, toObjectId);

					Log log = null;
					// single commit is requested and we already know it, no need for LogCommand
					if (commitsSize == 1 && toObjectId instanceof RevCommit) {
						log = new Log(cloneLocation, db, Collections.singleton((RevCommit) toObjectId), null, null, toRefId);
					} else {
						LogCommand lc = git.log();
						// set the commit range
						lc.add(toObjectId);
						lc.setMaxCount(this.commitsSize);
						Iterable<RevCommit> commits = lc.call();
						log = new Log(cloneLocation, db, commits, null, null, toRefId);
					}
					log.setPaging(1, commitsSize);
					children.put(branch.toJSON(log.toJSON()));
				}
			}
			result.put(ProtocolConstants.KEY_CHILDREN, children);
			result.put(ProtocolConstants.KEY_TYPE, Branch.TYPE);
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when listing branches for {0}", path);
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, msg, e);
		} finally {
			if (db != null) {
				// close the git repository
				db.close();
			}
		}
	}

}
