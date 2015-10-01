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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.servlets.GitCloneHandlerV1;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A job to perform a clone operation in the background
 */
public class CloneJob extends GitJob {

	private final ProjectInfo project;
	private final Clone clone;
	private final String user;
	private final String gitUserName;
	private final String gitUserMail;
	private String cloneLocation;
	private final boolean initProject;

	public CloneJob(Clone clone, String userRunningTask, CredentialsProvider credentials, String user, String cloneLocation, ProjectInfo project,
			String gitUserName, String gitUserMail, boolean initProject) {
		super(userRunningTask, true, (GitCredentialsProvider) credentials);
		this.clone = clone;
		this.user = user;
		this.project = project;
		this.gitUserName = gitUserName;
		this.gitUserMail = gitUserMail;
		this.cloneLocation = cloneLocation;
		this.initProject = initProject;
		setFinalMessage("Clone complete.");
		setTaskExpirationTime(TimeUnit.DAYS.toMillis(7));
	}

	public CloneJob(Clone clone, String userRunningTask, CredentialsProvider credentials, String user, String cloneLocation, ProjectInfo project,
			String gitUserName, String gitUserMail) {
		this(clone, userRunningTask, credentials, user, cloneLocation, project, gitUserName, gitUserMail, false);
	}

	public CloneJob(Clone clone, String userRunningTask, CredentialsProvider credentials, String user, String cloneLocation, ProjectInfo project,
			String gitUserName, String gitUserMail, boolean initProject, Object cookie) {
		this(clone, userRunningTask, credentials, user, cloneLocation, project, gitUserName, gitUserMail, initProject);
		this.cookie = (Cookie) cookie;
	}
	
	@Override
	public synchronized TaskInfo startTask() {
		TaskInfo task = super.startTask();
		task.setLengthComputable(true);
		return task;
	}

	private IStatus doClone(IProgressMonitor monitor) {
		EclipseGitProgressTransformer gitMonitor = new EclipseGitProgressTransformer(monitor, this);
		Repository repo = null;
		try {
			File cloneFolder = new File(clone.getContentLocation().getPath());
			if (!cloneFolder.exists()) {
				cloneFolder.mkdir();
			}
			CloneCommand cc = Git.cloneRepository();
			cc.setProgressMonitor(gitMonitor);
			cc.setBare(false);
			cc.setCredentialsProvider(credentials);
			cc.setDirectory(cloneFolder);
			cc.setRemote(Constants.DEFAULT_REMOTE_NAME);
			cc.setURI(clone.getUrl());
			//cc.setCloneSubmodules(true);
			
			if (this.cookie != null) {
				cc.setTransportConfigCallback(new TransportConfigCallback() {
					@Override
					public void configure(Transport t) {
						if (t instanceof TransportHttp && cookie != null) {
							HashMap<String, String> map = new HashMap<String, String>();
							map.put(GitConstants.KEY_COOKIE, cookie.getName() + "=" + cookie.getValue());
							((TransportHttp) t).setAdditionalHeaders(map);
						}
					}
				});
			}
			Git git = cc.call();

			if (monitor.isCanceled()) {
				return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
			}
			// Configure the clone, see Bug 337820
			GitCloneHandlerV1.doConfigureClone(git, user, gitUserName, gitUserMail);
			repo = git.getRepository();
			GitJobUtils.packRefs(repo, gitMonitor);
			if (monitor.isCanceled()) {
				return new Status(IStatus.CANCEL, GitActivator.PI_GIT, "Cancelled");
			}

			if (initProject) {
				File projectJsonFile = new File(cloneFolder.getPath() + File.separator + "project.json");
				if (!projectJsonFile.exists()) {
					PrintStream out = null;
					try {
						out = new PrintStream(new FileOutputStream(projectJsonFile));
						JSONObject projectjson = new JSONObject();

						String gitPath = clone.getUrl();
						if (gitPath.indexOf("://") > 0) {
							gitPath = gitPath.substring(gitPath.indexOf("://") + 3);
						}
						String[] segments = gitPath.split("/");
						String serverName = segments[0];
						if (serverName.indexOf("@") > 0) {
							serverName = serverName.substring(serverName.indexOf("@") + 1);
						}
						String repoName = segments[segments.length - 1];
						if (repoName.indexOf(".git") > 0) {
							repoName = repoName.substring(0, repoName.lastIndexOf(".git"));
						}
						projectjson.put("Name", repoName + " at " + serverName);
						out.print(projectjson.toString());
					} finally {
						if (out != null)
							out.close();
					}
				}
			}

		} catch (IOException e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e);
		} catch (CoreException e) {
			return e.getStatus();
		} catch (GitAPIException e) {
			return getGitAPIExceptionStatus(e, "Error cloning git repository");
		} catch (JGitInternalException e) {
			return getJGitInternalExceptionStatus(e, "Error cloning git repository");
		} catch (Exception e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e);
		} finally {
			if (repo != null) {
				repo.close();
			}
		}
		JSONObject jsonData = new JSONObject();
		try {
			jsonData.put(ProtocolConstants.KEY_LOCATION, URI.create(this.cloneLocation));
		} catch (JSONException e) {
			// Should not happen
		}

		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, jsonData);
	}

	@Override
	protected IStatus performJob(IProgressMonitor monitor) {
		IStatus result = doClone(monitor);
		if (result.isOK())
			return result;
		try {
			if (project != null)
				GitCloneHandlerV1.removeProject(user, project);
			else
				FileUtils.delete(URIUtil.toFile(clone.getContentLocation()), FileUtils.RECURSIVE);
		} catch (IOException e) {
			// log the secondary failure and return the original failure
			String msg = "An error occurred when cleaning up after a clone failure"; //$NON-NLS-1$
			LogHelper.log(new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return result;
	}
}
