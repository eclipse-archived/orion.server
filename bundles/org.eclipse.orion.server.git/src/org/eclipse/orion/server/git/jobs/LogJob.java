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
package org.eclipse.orion.server.git.jobs;

import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.objects.Log;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;

/**
 * A job to perform a git log in the background.
 */
public class LogJob extends GitJob {

	private final LogCommand logCommand;
	private final Log log;
	private final int page;
	private final int pageSize;
	private final URI logLocation;

	public LogJob(String userRunningTask, LogCommand logCommand, Log log, int page, int pageSize, URI logLocation) {
		super("Log", userRunningTask);
		this.logCommand = logCommand;
		this.log = log;
		this.page = page;
		this.pageSize = pageSize;
		this.logLocation = logLocation;
		this.task = createTask();
	}

	protected TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask(NLS.bind("Generating git log for {0}", logCommand.getRepository()), this.userId, true);
		info.setMessage(NLS.bind("Generating git log for {0} ...", logCommand.getRepository()));
		getTaskService().updateTask(info);
		return info;
	}

	private IStatus doLog() {
		try {
			Iterable<RevCommit> commits = logCommand.call();
			log.setCommits(commits);
			JSONObject result = log.toJSON(page, pageSize);
			// return the commits log as status message
			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, result);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when generating log for ref {0}", logCommand.getRepository());
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, msg, e);
		}
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		try {
			IStatus result = doLog();
			if (result.isOK()) {
				task.setResultLocation(logLocation);
				task.done(result);
				// set the message after updating the task with the result
				task.setMessage("Generating git log completed.");
			} else {
				task.done(result);
			}
			updateTask(task);
			//return the actual result so errors are logged, see bug 353190
			return result;
		} finally {
			cleanUp();
		}
	}
}
