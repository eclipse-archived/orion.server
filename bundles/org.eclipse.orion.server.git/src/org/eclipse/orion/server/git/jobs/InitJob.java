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
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.servlets.GitCloneHandlerV1;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A job to perform an init operation in the background
 */
public class InitJob extends GitJob {

	private final Clone clone;
	private final String user;
	private final String gitUserName;
	private final String gitUserMail;
	private String cloneLocation;

	public InitJob(Clone clone, String userRunningTask, String user, String cloneLocation, String gitUserName, String gitUserMail) {
		super(userRunningTask, true);
		this.clone = clone;
		this.user = user;
		this.gitUserName = gitUserName;
		this.gitUserMail = gitUserMail;
		this.cloneLocation = cloneLocation;
		setFinalMessage("Init complete.");
		setTaskExpirationTime(TimeUnit.DAYS.toMillis(7));
	}

	@Override
	public IStatus performJob() {
		Repository repository = null;
		try {
			InitCommand command = new InitCommand();
			File directory = new File(clone.getContentLocation());
			command.setDirectory(directory);
			repository = command.call().getRepository();
			Git git = new Git(repository);

			// configure the repo
			GitCloneHandlerV1.doConfigureClone(git, user, gitUserName, gitUserMail);

			// we need to perform an initial commit to workaround JGit bug 339610
			git.commit().setMessage("Initial commit").call();
		} catch (CoreException e) {
			return e.getStatus();
		} catch (GitAPIException e) {
			return getGitAPIExceptionStatus(e, "Error initializing git repository");
		} catch (JGitInternalException e) {
			return getJGitInternalExceptionStatus(e, "Error initializing git repository");
		} catch (Exception e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error initializing git repository", e);
		} finally {
			if (repository != null) {
				repository.close();
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

}
