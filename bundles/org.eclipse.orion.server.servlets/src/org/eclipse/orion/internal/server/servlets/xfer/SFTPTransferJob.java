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
package org.eclipse.orion.internal.server.servlets.xfer;

import com.jcraft.jsch.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Common base class for import/export over SFTP
 */
public abstract class SFTPTransferJob extends Job {

	protected final String host;
	protected final File localRoot;
	private final List<String> options;
	protected final String passphrase;
	protected final int port;
	protected final IPath remoteRoot;
	protected TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	protected final String user;

	public SFTPTransferJob(String userRunningTask, File localFile, String host, int port, IPath remotePath, String user, String passphrase, List<String> options) {
		super("Transfer over SFTP"); //$NON-NLS-1$
		this.localRoot = localFile;
		this.host = host;
		this.port = port;
		this.remoteRoot = remotePath;
		this.user = user;
		this.passphrase = passphrase;
		this.options = options;
		this.task = createTask(userRunningTask, false);
		task.setLengthComputable(true);
	}

	private void cleanUp() {
		taskService = null;
		if (taskServiceRef != null) {
			Activator.getDefault().getContext().ungetService(taskServiceRef);
			taskServiceRef = null;
		}
	}

	protected TaskInfo createTask(String userId, boolean keep) {
		TaskInfo info = getTaskService().createTask(userId, keep);
		getTaskService().updateTask(info);
		return info;
	}

	protected List<String> getOptions() {
		return options;
	}

	public TaskInfo getTask() {
		return task;
	}

	protected ITaskService getTaskService() {
		if (taskService == null) {
			BundleContext context = Activator.getDefault().getContext();
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
			JSch jsch = new JSch();
			IStatus result = null;
			try {
				Session session = jsch.getSession(user, host, port);
				session.setPassword(passphrase);
				//don't require host key to be in orion server's known hosts file
				session.setConfig("StrictHostKeyChecking", "no"); //$NON-NLS-1$ //$NON-NLS-2$
				session.connect();
				try {
					ChannelSftp channel = (ChannelSftp) session.openChannel("sftp"); //$NON-NLS-1$
					try {
						channel.connect();
						transferDirectory(channel, remoteRoot, localRoot);
					} finally {
						channel.disconnect();
					}
				} finally {
					session.disconnect();
				}
			} catch (Exception e) {
				String msg = NLS.bind("Transfer from {0} failed: {1}", host + remoteRoot, e.getMessage());
				result = new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e);
			}
			if (result == null) {
				//fill in message to client for successful result
				String msg = NLS.bind("Transfer complete: {0}", host + remoteRoot);
				result = new Status(IStatus.OK, Activator.PI_SERVER_SERVLETS, msg);
			}
			//update task for both good or bad result cases
			task.done(result);
			getTaskService().updateTask(task);
			return result;
		} finally {
			cleanUp();
		}
	}

	protected void taskItemLoaded() {
		task.setLoaded(task.getLoaded() + 1);
		getTaskService().updateTask(task);
	}

	protected void addTaskTotal(int total) {
		task.setTotal(task.getTotal() + total);
		getTaskService().updateTask(task);
	}

	/**
	 * Returns whether the given file name should be ignored during transfer.
	 */
	protected boolean shouldSkip(String fileName) {
		//skip parent and self references
		if (".".equals(fileName) || "..".equals(fileName))//$NON-NLS-1$ //$NON-NLS-2$
			return true;
		//skip git metadata
		return ".git".equals(fileName); //$NON-NLS-1$
	}

	/**
	 * Method overwritten by subclass to implement import/export
	 */
	protected abstract void transferDirectory(ChannelSftp channel, IPath remotePath, File localFile) throws SftpException, IOException;
}