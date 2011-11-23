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

import java.io.File;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.servlets.GitCloneHandlerV1;
import org.eclipse.osgi.util.NLS;

/**
 * A job to perform an init operation in the background
 */
public class InitJob extends GitJob {

	private final Clone clone;
	private final String user;
	private final String cloneLocation;

	public InitJob(Clone clone, String userRunningTask, String user, String cloneLocation) {
		super("Init", userRunningTask);
		this.clone = clone;
		this.user = user;
		this.cloneLocation = cloneLocation;
		this.task = createTask();
	}

	protected TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask(this.userId);
		info.setMessage(NLS.bind("Initializing repository {0}...", clone.getName()));
		getTaskService().updateTask(info);
		return info;
	}

	private IStatus doInit() {
		try {
			InitCommand command = new InitCommand();
			File directory = new File(clone.getContentLocation());
			command.setDirectory(directory);
			Repository repository = command.call().getRepository();
			Git git = new Git(repository);

			// configure the repo
			GitCloneHandlerV1.doConfigureClone(git, user);

			// we need to perform an initial commit to workaround JGit bug 339610
			git.commit().setMessage("Initial commit").call();
		} catch (CoreException e) {
			return e.getStatus();
		} catch (JGitInternalException e) {
			return getJGitInternalExceptionStatus(e, "An internal git error initializing git repository.");
		} catch (Exception e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error initializing git repository", e);
		}
		return Status.OK_STATUS;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		try {
			IStatus result = doInit();
			if (result.isOK()) {
				// save the clone metadata
				task.setResultLocation(cloneLocation);
				String message = "Init complete.";
				task.setMessage(message);
				result = new Status(IStatus.OK, GitActivator.PI_GIT, message);
			}
			task.done(result);
			updateTask(task);
			//return the actual result so errors are logged, see bug 353190
			return result;
		} finally {
			cleanUp();
		}
	}
}
