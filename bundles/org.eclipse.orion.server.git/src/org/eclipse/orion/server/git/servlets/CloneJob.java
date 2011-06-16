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
package org.eclipse.orion.server.git.servlets;

import java.io.File;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.*;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * A job to perform a clone operation in the background
 */
public class CloneJob extends GitJob {

	private final WebProject project;
	private final WebClone clone;
	private final TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	private final String user;
	private final String cloneLocation;

	public CloneJob(WebClone clone, CredentialsProvider credentials, String user, String cloneLocation, WebProject project) {
		super("Cloning", (GitCredentialsProvider) credentials); //$NON-NLS-1$
		this.clone = clone;
		this.user = user;
		this.cloneLocation = cloneLocation;
		this.task = createTask();
		this.project = project;
	}

	private TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask();
		info.setMessage(NLS.bind("Cloning {0}...", clone.getUrl()));
		getTaskService().updateTask(info);
		return info;
	}

	private IStatus doClone() {
		try {
			File cloneFolder = new File(clone.getContentLocation().getPath());
			if (!cloneFolder.exists()) {
				cloneFolder.mkdir();
			} else {
				// workaround until http://egit.eclipse.org/r/#change,3518 is fixed
				File dotGit = new File(cloneFolder, Constants.DOT_GIT);
				if (dotGit.exists())
					throw new IOException("Destination folder already exists and contains a repository");
			}
			CloneCommand cc = Git.cloneRepository();
			cc.setBare(false);
			cc.setCredentialsProvider(credentials);
			cc.setDirectory(cloneFolder);
			cc.setRemote(Constants.DEFAULT_REMOTE_NAME);
			cc.setURI(clone.getUrl());
			Git git = cc.call();

			// Configure the clone, see Bug 337820
			task.setMessage(NLS.bind("Configuring {0}...", clone.getUrl()));
			updateTask();
			doConfigureClone(git);
			git.getRepository().close();
		} catch (IOException e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e);
		} catch (CoreException e) {
			return e.getStatus();
		} catch (JGitInternalException e) {
			return getJGitInternalExceptionStatus(e, "An internal git error cloning git remote");
		} catch (Exception e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e);
		}
		return Status.OK_STATUS;
	}

	private void doConfigureClone(Git git) throws IOException, CoreException {
		StoredConfig config = git.getRepository().getConfig();
		IOrionUserProfileNode userNode = UserServiceHelper.getDefault().getUserProfileService().getUserProfileNode(user, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);
		if (userNode.get(GitConstants.KEY_NAME, null) != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, userNode.get(GitConstants.KEY_NAME, null));
		if (userNode.get(GitConstants.KEY_MAIL, null) != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, userNode.get(GitConstants.KEY_MAIL, null));
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_FILEMODE, false);
		config.save();
	}

	public TaskInfo getTask() {
		return task;
	}

	private ITaskService getTaskService() {
		if (taskService == null) {
			BundleContext context = GitActivator.getDefault().getBundleContext();
			if (taskServiceRef == null) {
				taskServiceRef = context.getServiceReference(ITaskService.class);
				if (taskServiceRef == null)
					throw new IllegalStateException("Task service not available");
			}
			taskService = context.getService(taskServiceRef);
			if (taskService == null)
				throw new IllegalStateException("Task service not available");
		}
		return taskService;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			result = doClone();
			// save the clone metadata
			if (result.isOK()) {
				task.setResultLocation(cloneLocation);
				String message = "Clone complete.";
				task.setMessage(message);
				result = new Status(IStatus.OK, GitActivator.PI_GIT, message);
			} else {
				try {
					if (project != null)
						GitCloneHandlerV1.removeProject(user, project);
					else
						FileUtils.delete(URIUtil.toFile(clone.getContentLocation()), FileUtils.RECURSIVE);
				} catch (IOException e) {
					String msg = "An error occured when cleaning up after a clone failure";
					result = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
				}
			}
			task.done(result);
			updateTask();
		} finally {
			cleanUp();
		}
		//return the actual result so errors are logged
		return result;
	}

	private void cleanUp() {
		taskService = null;
		if (taskServiceRef != null) {
			GitActivator.getDefault().getBundleContext().ungetService(taskServiceRef);
			taskServiceRef = null;
		}
	}

	private void updateTask() {
		getTaskService().updateTask(task);
	}

}
