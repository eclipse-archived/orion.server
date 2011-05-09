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
import java.util.Locale;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.jsch.HostFingerprintException;

/**
 * Base class for all Git jobs.
 *
 */
public abstract class GitJob extends Job {

	private static JSchException getJSchException(Throwable e) {
		if (e instanceof JSchException) {
			return (JSchException) e;
		}
		if (e.getCause() != null) {
			return getJSchException(e.getCause());
		}
		return null;
	}

	public static IStatus getJGitInternalExceptionStatus(JGitInternalException e, String message) {
		JSchException jschEx = getJSchException(e);
		if (jschEx != null && jschEx instanceof HostFingerprintException) {
			HostFingerprintException cause = (HostFingerprintException) jschEx;
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, cause.getMessage(), cause.formJson(), cause);
		}
		//JSch handles auth fail by exception message
		if (jschEx.getMessage() != null && jschEx.getMessage().toLowerCase(Locale.ENGLISH).contains("auth fail")) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_UNAUTHORIZED, jschEx.getMessage(), null, jschEx);
		}
		return new Status(IStatus.ERROR, GitActivator.PI_GIT, message, e);
	}

	public GitJob(String name) {
		super(name);
	}

}
