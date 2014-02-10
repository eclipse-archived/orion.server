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
package org.eclipse.orion.server.git.jobs;

import com.jcraft.jsch.JSchException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskJob;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.orion.server.jsch.HostFingerprintException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Base class for all Git jobs.
 *
 */
@SuppressWarnings("restriction")
public abstract class GitJob extends TaskJob {

	private static final String KEY_SCHEME = "Scheme"; //$NON-NLS-1$
	private static final String KEY_PORT = "Port"; //$NON-NLS-1$
	private static final String KEY_PASSWORD = "Password"; //$NON-NLS-1$
	public static final String KEY_HUMANISH_NAME = "HumanishName"; //$NON-NLS-1$
	public static final String KEY_URL = "Url"; //$NON-NLS-1$
	public static final String KEY_USER = "User"; //$NON-NLS-1$
	public static final String KEY_HOST = "Host"; //$NON-NLS-1$
	protected GitCredentialsProvider credentials;
	protected Cookie cookie;

	private static JSchException getJSchException(Throwable e) {
		if (e instanceof JSchException) {
			return (JSchException) e;
		}
		if (e.getCause() != null) {
			return getJSchException(e.getCause());
		}
		return null;
	}

	private JSONObject addRepositoryInfo(JSONObject object) {
		try {
			if (credentials != null) {
				object.put(KEY_URL, credentials.getUri().toString());
				if (credentials.getUri().getUser() != null) {
					object.put(KEY_USER, credentials.getUri().getUser());
				}
				if (credentials.getUri().getHost() != null) {
					object.put(KEY_HOST, credentials.getUri().getHost());
				}
				if (credentials.getUri().getHumanishName() != null) {
					object.put(KEY_HUMANISH_NAME, credentials.getUri().getHumanishName());
				}
				if (credentials.getUri().getPass() != null) {
					object.put(KEY_PASSWORD, credentials.getUri().getPass());
				}
				if (credentials.getUri().getPort() > 0) {
					object.put(KEY_PORT, credentials.getUri().getPort());
				}
				if (credentials.getUri().getScheme() != null) {
					object.put(KEY_SCHEME, credentials.getUri().getScheme());
				}

			}
		} catch (JSONException e) {
			// ignore, should always be able to put string
		}
		return object;
	}

	IStatus getExceptionStatus(Exception e, String message) {
		JSchException jschEx = getJSchException(e);
		if (jschEx != null && jschEx instanceof HostFingerprintException) {
			HostFingerprintException cause = (HostFingerprintException) jschEx;
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, cause.getMessage(), addRepositoryInfo(cause.formJson()), cause);
		}
		//JSch handles auth fail by exception message
		if (jschEx != null && jschEx.getMessage() != null && jschEx.getMessage().toLowerCase(Locale.ENGLISH).contains("auth fail")) { //$NON-NLS-1$
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_UNAUTHORIZED, jschEx.getMessage(), addRepositoryInfo(new JSONObject()), jschEx);
		}

		//Log connection problems directly
		if (e.getCause() instanceof TransportException) {
			TransportException cause = (TransportException) e.getCause();
			if (matchMessage(JGitText.get().serviceNotPermitted, cause.getMessage())) {
				//HTTP connection problems are distinguished by exception message
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, cause.getMessage(), addRepositoryInfo(new JSONObject()), cause);
			} else if (matchMessage(JGitText.get().notAuthorized, cause.getMessage())) {
				//HTTP connection problems are distinguished by exception message
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_UNAUTHORIZED, cause.getMessage(), addRepositoryInfo(new JSONObject()), cause);
			} else if (cause.getMessage().endsWith("username must not be null.") || cause.getMessage().endsWith("host must not be null.")) { //$NON-NLS-1$ //$NON-NLS-2$
				// see com.jcraft.jsch.JSch#getSession(String, String, int)
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, cause.getMessage(), addRepositoryInfo(new JSONObject()), cause);
			} else if (e instanceof GitAPIException) {
				//Other HTTP connection problems reported directly
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, addRepositoryInfo(new JSONObject()), e);
			} else {
				//Other HTTP connection problems reported directly
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, addRepositoryInfo(new JSONObject()), cause);
			}

		}

		return new Status(IStatus.ERROR, GitActivator.PI_GIT, message, e.getCause() == null ? e : e.getCause());
	}

	IStatus getGitAPIExceptionStatus(GitAPIException e, String message) {
		return getExceptionStatus(e, message);
	}

	IStatus getJGitInternalExceptionStatus(JGitInternalException e, String message) {
		IStatus status = getExceptionStatus(e, message);
		//TODO uncomment this when fix in jgit is merged
		//		if (status instanceof ServerStatus) {
		//			LogHelper.log(new Status(IStatus.WARNING, GitActivator.PI_GIT, "JGitInternalException should not be thrown for authentication errors. See https://git.eclipse.org/r/#/c/6207/", e));
		//		}
		return status;
	}

	public GitJob(String userRunningTask, boolean keep, GitCredentialsProvider credentials) {
		super(userRunningTask, keep);
		this.credentials = credentials;
		this.cookie = GitUtils.getSSOToken();
	}

	public GitJob(String userRunningTask, boolean keep) {
		this(userRunningTask, keep, null);
	}

	/**
	 * Check if message matches or contains pattern in {@link MessageFormat} format.
	 *  
	 * @param pattern 
	 * @param message
	 * @return <code>true</code> if the messages match, and <code>false</code> otherwise
	 * @see MessageFormat
	 */
	private static boolean matchMessage(String pattern, String message) {
		if (message == null) {
			return false;
		}
		int argsNum = 0;
		for (int i = 0; i < pattern.length(); i++) {
			if (pattern.charAt(i) == '{') {
				argsNum++;
			}
		}
		Object[] args = new Object[argsNum];
		for (int i = 0; i < args.length; i++) {
			args[i] = ".*"; //$NON-NLS-1$
		}

		return Pattern.matches(".*" + MessageFormat.format(pattern, args) + ".*", message); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	protected IStatus run(IProgressMonitor progressMonitor) {
		GitUtils.setSSOToken(this.cookie);
		return super.run(progressMonitor);
	}
}
