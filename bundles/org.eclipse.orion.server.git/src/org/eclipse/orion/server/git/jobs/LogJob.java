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
package org.eclipse.orion.server.git.jobs;

import java.io.File;
import java.net.URI;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.objects.Log;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A job to perform a git log in the background.
 */
public class LogJob extends GitJob {

	private IPath filePath;
	private URI cloneLocation;
	private int page;
	private int pageSize;
	private ObjectId toObjectId;
	private ObjectId fromObjectId;
	private Ref toRefId;
	private Ref fromRefId;
	private String refIdsRange;
	private String pattern;
	private String messageFilter;
	private String authorFilter;
	private String committerFilter;
	private String sha1Filter;
	private String fromDate;
	private String toDate;
	private boolean mergeBaseFilter;
	private static Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.git");

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
	 * @param messageFilter
	 *            string used to filter log messages
	 * @param authorFilter
	 *            string used to filter author messages
	 * @param committerFilter
	 *            string used to filter committer messages
	 * @param sha1Filter
	 *            string used to filter commits by sha1
	 * @param baseLocation
	 *            URI used as a base for generating next and previous page links. Should not contain any parameters.
	 */
	public LogJob(String userRunningTask, IPath filePath, URI cloneLocation, int page, int pageSize, ObjectId toObjectId, ObjectId fromObjectId, Ref toRefId,
			Ref fromRefId, String refIdsRange, String pattern, String messageFilter, String authorFilter, String committerFilter, String sha1Filter,
			boolean mergeBaseFilter, String fromDate, String toDate) {
		super(userRunningTask, false);
		this.filePath = filePath;
		this.cloneLocation = cloneLocation;
		this.page = page;
		this.pageSize = pageSize;
		this.toObjectId = toObjectId;
		this.fromObjectId = fromObjectId;
		this.toRefId = toRefId;
		this.fromRefId = fromRefId;
		this.refIdsRange = refIdsRange;
		this.pattern = pattern;
		this.messageFilter = messageFilter;
		this.authorFilter = authorFilter;
		this.committerFilter = committerFilter;
		this.sha1Filter = sha1Filter;
		this.fromDate = fromDate;
		this.toDate = toDate;
		this.mergeBaseFilter = mergeBaseFilter;
		setFinalMessage("Generating git log completed.");
	}

	@Override
	protected IStatus performJob() {
		Repository db = null;
		LogCommand logCommand = null;
		try {
			File gitDir = GitUtils.getGitDir(filePath);
			db = FileRepositoryBuilder.create(gitDir);
			int aheadCount = 0, behindCount = 0, maxCount = -1;
			if (mergeBaseFilter) {
				RevWalk walk = new RevWalk(db);
				try {
					walk.setRevFilter(RevFilter.MERGE_BASE);
					RevCommit toRevCommit = walk.lookupCommit(toObjectId);
					walk.markStart(toRevCommit);
					RevCommit fromRevCommit = walk.lookupCommit(fromObjectId);
					walk.markUninteresting(fromRevCommit);
					RevCommit next = walk.next();
					walk.reset();
					walk.setRevFilter(RevFilter.ALL);
					aheadCount = RevWalkUtils.count(walk, toRevCommit, next);
					behindCount = RevWalkUtils.count(walk, fromRevCommit, next);
					if (next != null) {
						toObjectId = next.toObjectId();
						fromObjectId = null;
					} else {
						// There is no merge base, return an empty log
						maxCount = 0;
					}
				} finally {
					walk.dispose();
				}
			}

			logCommand = new LogCommand(db);
			if (refIdsRange != null) {
				// set the commit range
				logCommand.add(toObjectId);

				if (fromObjectId != null) {
					logCommand.not(fromObjectId);
				}
			} else {
				// git log --all
				logCommand.all();
			}
			Log log = new Log(cloneLocation, db, null /* collected by the job */, pattern, toRefId, fromRefId);

			log.setMergeBaseFilter(mergeBaseFilter);

			if (messageFilter != null && messageFilter.length() > 0) {
				log.setMessagePattern(messageFilter);
				logCommand.setMessageFilter(messageFilter);
			}

			if (authorFilter != null && authorFilter.length() > 0) {
				log.setAuthorPattern(authorFilter);
				logCommand.setAuthFilter(authorFilter);
			}

			if (committerFilter != null && committerFilter.length() > 0) {
				log.setCommitterPattern(committerFilter);
				logCommand.setCommitterFilter(committerFilter);
			}

			if (sha1Filter != null && sha1Filter.length() > 0) {
				log.setSHA1Pattern(sha1Filter);
				logCommand.setSHA1Filter(sha1Filter);
			}

			if (fromDate != null && fromDate.length() > 0) {
				log.setFromDate(fromDate);
				if (toDate != null && toDate.length() > 0) {
					log.setToDate(toDate);
					logCommand.setDateFilter(fromDate, toDate);
				} else {
					logCommand.setDateFilter(fromDate, null);
				}
			} else if (toDate != null && toDate.length() > 0) {
				log.setToDate(toDate);
				logCommand.setDateFilter(null, toDate);
			}

			if (page > 0) {
				logCommand.setSkip((page - 1) * pageSize);
				logCommand.setMaxCount(pageSize + 1); // to check if next page
														// link is needed
			}
			if (pattern != null && !pattern.isEmpty()) {
				logCommand.addPath(pattern);
			}
			log.setPaging(page, pageSize);
			if (maxCount != -1) {
				logCommand.setMaxCount(maxCount);
			}

			Iterable<RevCommit> commits = logCommand.call();
			log.setCommits(commits);
			JSONObject result = log.toJSON();
			if (mergeBaseFilter) {
				result.put(GitConstants.KEY_BEHIND_COUNT, behindCount);
				result.put(GitConstants.KEY_AHEAD_COUNT, aheadCount);
			}

			// return the commits log as status message
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when generating log for ref {0}", logCommand != null ? logCommand.getRepository() : filePath);
			logger.error(msg, e);
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, msg, e);
		} finally {
			if (db != null) {
				// close the git repository
				db.close();
			}
		}
	}
}
