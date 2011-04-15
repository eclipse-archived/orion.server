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
package org.eclipse.orion.internal.server.servlets.xfer;

import com.jcraft.jsch.*;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import java.io.*;
import java.util.Vector;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.xfer.SFTPImport.BasicUserInfo;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Implementation of background import into workspace over SFTP.
 */
public class SFTPImportJob extends Job {

	private final File destinationRoot;
	private final String host;
	private final String passphrase;
	private final int port;
	private final IPath sourceRoot;
	private TaskInfo task;
	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;
	private final String user;

	public SFTPImportJob(File destination, String host, int port, IPath sourcePath, String user, String passphrase) {
		super("Import over SFTP"); //$NON-NLS-1$
		this.destinationRoot = destination;
		this.host = host;
		this.port = port;
		this.sourceRoot = sourcePath;
		this.user = user;
		this.passphrase = passphrase;
		this.task = createTask();
	}

	private TaskInfo createTask() {
		TaskInfo info = getTaskService().createTask();
		info.setMessage(NLS.bind("Importing {0}...", host + sourceRoot.toString()));
		getTaskService().updateTask(info);
		return info;
	}

	private void doImportDirectory(ChannelSftp channel, IPath sourcePath, File destination) throws SftpException, IOException {
		task.setMessage(NLS.bind("Importing {0}...", host + sourcePath.toString()));
		getTaskService().updateTask(task);
		destination.mkdirs();
		@SuppressWarnings("unchecked")
		Vector<LsEntry> children = channel.ls(sourcePath.toString());
		for (LsEntry child : children) {
			String childName = child.getFilename();
			//skip self and parent references
			if (".".equals(childName) || "..".equals(childName)) //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			if (child.getAttrs().isDir()) {
				doImportDirectory(channel, sourcePath.append(childName), new File(destination, childName));
			} else {
				doImportFile(channel, sourcePath.append(childName), new File(destination, childName));
			}
		}
	}

	private void doImportFile(ChannelSftp channel, IPath sourcePath, File destination) throws IOException, SftpException {
		IOUtilities.pipe(channel.get(sourcePath.toString()), new FileOutputStream(destination), true, true);
	}

	public TaskInfo getTask() {
		return task;
	}

	private ITaskService getTaskService() {
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
				session.setUserInfo(new BasicUserInfo(passphrase, passphrase));
				session.connect();
				try {
					ChannelSftp channel = (ChannelSftp) session.openChannel("sftp"); //$NON-NLS-1$
					try {
						channel.connect();
						doImportDirectory(channel, sourceRoot, destinationRoot);
					} finally {
						channel.disconnect();
					}
				} finally {
					session.disconnect();
				}
			} catch (Exception e) {
				String msg = NLS.bind("Import from {0} failed: {1}", host + sourceRoot, e.getMessage());
				result = new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg, e);
			}
			if (result == null) {
				//fill in message to client for successful result
				String msg = NLS.bind("Import from {0} complete", host + sourceRoot);
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

	private void cleanUp() {
		taskService = null;
		if (taskServiceRef != null) {
			Activator.getDefault().getContext().ungetService(taskServiceRef);
			taskServiceRef = null;
		}
	}
}