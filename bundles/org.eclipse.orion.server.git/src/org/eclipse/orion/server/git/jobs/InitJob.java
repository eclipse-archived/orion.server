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
import java.net.URI;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
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

	public InitJob(Clone clone, String userRunningTask, String user, String cloneLocation) {
		super(NLS.bind("Initializing repository {0}", clone.getName()), userRunningTask, NLS.bind("Initializing repository {0}...", clone.getName()), false, false);
		this.clone = clone;
		this.user = user;
		setFinalLocation(URI.create(cloneLocation));
		setFinalMessage("Init complete.");
	}

	public IStatus performJob() {
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
		} catch (GitAPIException e) {
			return getJGitAPIExceptionStatus(e, "An internal git error initializing git repository.");
		} catch (Exception e) {
			return new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error initializing git repository", e);
		}
		return Status.OK_STATUS;
	}

}
