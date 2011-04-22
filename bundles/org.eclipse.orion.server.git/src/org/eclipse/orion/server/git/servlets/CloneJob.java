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

import com.jcraft.jsch.JSchException;
import java.io.File;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.jsch.HostFingerprintException;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.useradmin.UserServiceHelper;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * A job to perform a clone operation in the background
 */
public class CloneJob extends Job {

	private final WebClone clone;
	private final CredentialsProvider credentials;
	private final TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	private final String userId;
	private final String cloneLocation;

	public CloneJob(WebClone clone, CredentialsProvider credentials, String user, String cloneLocation) {
		super("Cloning"); //$NON-NLS-1$
		this.clone = clone;
		this.credentials = credentials;
		this.userId = user;
		this.cloneLocation = cloneLocation;
		this.task = createTask();
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
			if (!cloneFolder.exists())
				cloneFolder.mkdir();
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
		} catch (IOException e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e); //$NON-NLS-1$
		} catch (CoreException e) {
			return e.getStatus();
		} catch (JGitInternalException e) {
			JSchException jschEx = getJSchException(e);
			if (jschEx != null && jschEx instanceof HostFingerprintException) {
				HostFingerprintException cause = (HostFingerprintException) jschEx;
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, cause.getMessage(), cause.formJson(), cause);
			} else {
				return new Status(IStatus.ERROR, GitActivator.PI_GIT, "An internal git error cloning git remote", e); //$NON-NLS-1$
			}
		} catch (Exception e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e); //$NON-NLS-1$
		}
		return Status.OK_STATUS;
	}

	private void doConfigureClone(Git git) throws IOException, CoreException {
		StoredConfig config = git.getRepository().getConfig();
		IOrionUserProfileNode userNode = UserServiceHelper.getDefault().getUserProfileService().getUserProfileNode(userId, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);
		if (userNode.get(GitConstants.KEY_NAME, null) != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, userNode.get(GitConstants.KEY_NAME, null));
		if (userNode.get(GitConstants.KEY_MAIL, null) != null)
			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, userNode.get(GitConstants.KEY_MAIL, null));
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
					throw new IllegalStateException("Task service not available"); //$NON-NLS-1$
			}
			taskService = context.getService(taskServiceRef);
			if (taskService == null)
				throw new IllegalStateException("Task service not available"); //$NON-NLS-1$
		}
		return taskService;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		try {
			IStatus result = doClone();
			// save the clone metadata
			try {
				if (result.isOK()) {
					clone.save();
					task.setResultLocation(cloneLocation);
					String message = NLS.bind("Repository cloned. You may now link to {0}", clone.getContentLocation());
					task.setMessage(message);
					result = new Status(IStatus.OK, GitActivator.PI_GIT, message);
				} else {
					FileUtils.delete(URIUtil.toFile(clone.getContentLocation()), FileUtils.RECURSIVE);
					clone.remove();
				}
			} catch (CoreException e) {
				String msg = "Error persisting clone state"; //$NON-NLS-1$
				result = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
			} catch (IOException e) {
				String msg = "Error persisting clone state"; //$NON-NLS-1$
				result = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
			}
			task.done(result);
			updateTask();
		} finally {
			cleanUp();
		}
		return Status.OK_STATUS;
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

	private static JSchException getJSchException(Throwable e) {
		if (e instanceof JSchException) {
			return (JSchException) e;
		}
		if (e.getCause() != null) {
			return getJSchException(e.getCause());
		}
		return null;
	}

}
