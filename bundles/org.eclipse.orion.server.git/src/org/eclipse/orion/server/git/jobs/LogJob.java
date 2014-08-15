/*******************************************************************************
 * Copyright (c) 2011, 2013 IBM Corporation and others.
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
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.objects.Log;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;

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

	/**
	 * Creates job with given page range and adding <code>commitsSize</code> commits to every branch.
	 * @param userRunningTask
	 * @param repositoryPath
	 * @param cloneLocation
	 * @param commitsSize user 0 to omit adding any log, only CommitLocation will be attached 
	 * @param pageNo
	 * @param pageSize use negative to indicate that all commits need to be returned
	 * @param messageFilter string used to filter log messages
	 * @param authorFilter string used to filter author messages
	 * @param committerFilter string used to filter committer messages
	 * @param baseLocation URI used as a base for generating next and previous page links. Should not contain any parameters.
	 */
	public LogJob(String userRunningTask, IPath filePath, URI cloneLocation, int page, int pageSize, ObjectId toObjectId, ObjectId fromObjectId, Ref toRefId, Ref fromRefId, String refIdsRange, String pattern, String messageFilter, String authorFilter, String committerFilter) {
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
		setFinalMessage("Generating git log completed.");
	}

	@Override
	protected IStatus performJob() {
		Repository db = null;
		LogCommand logCommand = null;
		try {
			File gitDir = GitUtils.getGitDir(filePath);
			db = FileRepositoryBuilder.create(gitDir);
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
			if (messageFilter != null) {
				logCommand.addMessageFilter(messageFilter);
			}

			if (authorFilter != null) {
				logCommand.addAuthFilter(authorFilter);
			}

			if (committerFilter != null) {
				logCommand.addCommitterFilter(committerFilter);
			}

			if (page > 0) {
				logCommand.setSkip((page - 1) * pageSize);
				logCommand.setMaxCount(pageSize + 1); // to check if next page link is needed
			}
			if (pattern != null && !pattern.isEmpty()) {
				logCommand.addPath(pattern);
			}
			Log log = new Log(cloneLocation, db, null /* collected by the job */, pattern, toRefId, fromRefId);
			log.setPaging(page, pageSize);

			Iterable<RevCommit> commits = logCommand.call();
			log.setCommits(commits);
			JSONObject result = log.toJSON();
			// return the commits log as status message
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when generating log for ref {0}", logCommand.getRepository());
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, msg, e);
		} finally {
			if (db != null) {
				// close the git repository
				db.close();
			}
		}
	}

}
