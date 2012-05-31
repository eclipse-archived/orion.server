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
import java.io.IOException;
import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.objects.Clone;
import org.eclipse.orion.server.git.servlets.GitCloneHandlerV1;
import org.eclipse.osgi.util.NLS;

/**
 * A job to perform a clone operation in the background
 */
public class CloneJob extends GitJob {

	private final WebProject project;
	private final Clone clone;
	private final String user;

	public CloneJob(Clone clone, String userRunningTask, CredentialsProvider credentials, String user, String cloneLocation, WebProject project) {
		super(NLS.bind("Cloning {0}", clone.getUrl()), userRunningTask, NLS.bind("Cloning {0}...", clone.getUrl()), false, false, (GitCredentialsProvider) credentials); //$NON-NLS-1$
		this.clone = clone;
		this.user = user;
		this.project = project;
		setFinalLocation(URI.create(cloneLocation));
		setFinalMessage("Clone complete.");
	}

	private IStatus doClone() {
		try {
			File cloneFolder = new File(clone.getContentLocation().getPath());
			if (!cloneFolder.exists()) {
				cloneFolder.mkdir();
			}
			CloneCommand cc = Git.cloneRepository();
			cc.setBare(false);
			cc.setCredentialsProvider(credentials);
			cc.setDirectory(cloneFolder);
			cc.setRemote(Constants.DEFAULT_REMOTE_NAME);
			cc.setURI(clone.getUrl());
			Git git = cc.call();

			// Configure the clone, see Bug 337820
			setMessage(NLS.bind("Configuring {0}...", clone.getUrl()));
			GitCloneHandlerV1.doConfigureClone(git, user);
			git.getRepository().close();
		} catch (IOException e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e);
		} catch (CoreException e) {
			return e.getStatus();
		} catch (GitAPIException e) {
			return getJGitAPIExceptionStatus(e, "An internal git error cloning git repository");
		} catch (Exception e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error cloning git repository", e);
		}
		return Status.OK_STATUS;
	}

	@Override
	protected IStatus performJob() {
		IStatus result = doClone();
		if (result.isOK()) {
			return result;
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
			return result;
		}
	}
}
