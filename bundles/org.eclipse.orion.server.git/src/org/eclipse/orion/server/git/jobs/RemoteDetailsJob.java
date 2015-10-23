/*******************************************************************************
 * Copyright (c) 2012, 2014 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.jobs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ConfigConstants;
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
import org.eclipse.orion.server.git.objects.Log;
import org.eclipse.orion.server.git.objects.Remote;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;

public class RemoteDetailsJob extends GitJob {

	private IPath path;
	private URI cloneLocation;
	private int commitsSize;
	private int pageNo;
	private int pageSize;
	private String baseLocation;
	private String configName;
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
	 */
	public RemoteDetailsJob(String userRunningTask, String configName, IPath repositoryPath, URI cloneLocation, int commitsSize, int pageNo, int pageSize,
			String baseLocation, String filter) {
		super(userRunningTask, false);
		this.path = repositoryPath;
		this.cloneLocation = cloneLocation;
		this.commitsSize = commitsSize;
		this.pageNo = pageNo;
		this.pageSize = pageSize;
		this.baseLocation = baseLocation;
		this.configName = configName;
		this.nameFilter = filter;
	}

	/**
	 * Creates job returning list of all branches.
	 * 
	 * @param userRunningTask
	 * @param repositoryPath
	 * @param cloneLocation
	 */
	public RemoteDetailsJob(String userRunningTask, String configName, IPath repositoryPath, URI cloneLocation) {
		this(userRunningTask, configName, repositoryPath, cloneLocation, 0, 0, -1, null, "");
	}

	/**
	 * Creates job returning list of all branches adding <code>commitsSize</code> commits to every branch.
	 * 
	 * @param userRunningTask
	 * @param repositoryPath
	 * @param cloneLocation
	 * @param commitsSize
	 * @param filterParm
	 */
	public RemoteDetailsJob(String userRunningTask, String configName, IPath repositoryPath, URI cloneLocation, int commitsSize, String filterParm) {
		this(userRunningTask, configName, repositoryPath, cloneLocation, commitsSize, 0, -1, null, filterParm);
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
	protected IStatus performJob(IProgressMonitor monitor) {
		Repository db = null;
		try {
			File gitDir = GitUtils.getGitDir(path);
			db = FileRepositoryBuilder.create(gitDir);
			Git git = Git.wrap(db);
			Set<String> configNames = db.getConfig().getSubsections(ConfigConstants.CONFIG_REMOTE_SECTION);
			for (String configN : configNames) {
				if (configN.equals(configName)) {
					Remote remote = new Remote(cloneLocation, db, configN);
					JSONObject result = remote.toJSON();
					if (!result.has(ProtocolConstants.KEY_CHILDREN)) {
						return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
					}
					JSONArray children = result.getJSONArray(ProtocolConstants.KEY_CHILDREN);
					JSONArray filteredChildren = new JSONArray();
					if (children.length() == 0 || (commitsSize == 0 && pageSize < 0)) {
						if (nameFilter != null && !nameFilter.equals("")) {
							for (int i = 0; i < children.length(); i++) {
								JSONObject test = (JSONObject) children.get(i);
								String name = (String) test.get("Name");
								if (name.toLowerCase().contains(nameFilter.toLowerCase())) {
									filteredChildren.put(test);
								}
							}
							result.put(ProtocolConstants.KEY_CHILDREN, filteredChildren);
						}
						return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
					}

					int firstChild = pageSize > 0 ? pageSize * (pageNo - 1) : 0;
					int lastChild = pageSize > 0 ? firstChild + pageSize - 1 : children.length() - 1;
					lastChild = lastChild > children.length() - 1 ? children.length() - 1 : lastChild;
					if (pageNo > 1 && baseLocation != null) {
						String prev = baseLocation + "?page=" + (pageNo - 1) + "&pageSize=" + pageSize;
						if (commitsSize > 0) {
							prev += "&" + GitConstants.KEY_TAG_COMMITS + "=" + commitsSize;
						}
						result.put(ProtocolConstants.KEY_PREVIOUS_LOCATION, prev);
					}
					if (lastChild < children.length() - 1) {
						String next = baseLocation + "?page=" + (pageNo + 1) + "&pageSize=" + pageSize;
						if (commitsSize > 0) {
							next += "&" + GitConstants.KEY_TAG_COMMITS + "=" + commitsSize;
						}
						result.put(ProtocolConstants.KEY_NEXT_LOCATION, next);
					}

					JSONArray newChildren = new JSONArray();
					for (int i = firstChild; i <= lastChild; i++) {
						if (monitor.isCanceled()) {
							return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
						}
						JSONObject branch = children.getJSONObject(i);
						if (commitsSize == 0) {
							newChildren.put(branch);
						} else {
							LogCommand lc = git.log();
							String branchName = branch.getString(ProtocolConstants.KEY_ID);
							ObjectId toObjectId = db.resolve(branchName);
							Ref toRefId = db.getRef(branchName);
							if (toObjectId == null) {
								String msg = NLS.bind("No ref or commit found: {0}", branchName);
								return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
							}
							toObjectId = getCommitObjectId(db, toObjectId);

							// set the commit range
							lc.add(toObjectId);
							lc.setMaxCount(this.commitsSize);
							Iterable<RevCommit> commits = lc.call();
							Log log = new Log(cloneLocation, db, commits, null, null, toRefId);
							log.setPaging(1, commitsSize);
							branch.put(GitConstants.KEY_TAG_COMMIT, log.toJSON());
							newChildren.put(branch);
						}
					}

					result.put(ProtocolConstants.KEY_CHILDREN, newChildren);

					return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
				}
			}
			String msg = NLS.bind("Couldn't find remote : {0}", configName);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
		} catch (Exception e) {
			String msg = NLS.bind("Couldn't get remote details : {0}", configName);
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, msg, e);
		} finally {
			if (db != null) {
				db.close();
			}
		}
	}
}
