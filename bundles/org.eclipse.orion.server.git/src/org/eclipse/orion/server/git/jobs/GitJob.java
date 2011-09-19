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

import com.jcraft.jsch.JSchException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.ITaskService;
import org.eclipse.orion.server.core.tasks.TaskInfo;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.jsch.HostFingerprintException;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Base class for all Git jobs.
 *
 */
public abstract class GitJob extends Job {

	private ITaskService taskService;
	private ServiceReference<ITaskService> taskServiceRef;

	ITaskService getTaskService() {
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

	void cleanUp() {
		taskService = null;
		if (taskServiceRef != null) {
			GitActivator.getDefault().getBundleContext().ungetService(taskServiceRef);
			taskServiceRef = null;
		}
	}

	protected void updateTask(TaskInfo task) {
		getTaskService().updateTask(task);
	}

	private static final String KEY_SCHEME = "Scheme"; //$NON-NLS-1$
	private static final String KEY_PORT = "Port"; //$NON-NLS-1$
	private static final String KEY_PASSWORD = "Password"; //$NON-NLS-1$
	public static final String KEY_HUMANISH_NAME = "HumanishName"; //$NON-NLS-1$
	public static final String KEY_URL = "Url"; //$NON-NLS-1$
	public static final String KEY_USER = "User"; //$NON-NLS-1$
	public static final String KEY_HOST = "Host"; //$NON-NLS-1$
	protected GitCredentialsProvider credentials;

	private static JSchException getJSchException(Throwable e) {
		if (e instanceof JSchException) {
			return (JSchException) e;
		}
		if (e.getCause() != null) {
			return getJSchException(e.getCause());
		}
		return null;
	}

	public JSONObject addRepositoryInfo(JSONObject object) {
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

	public IStatus getJGitInternalExceptionStatus(JGitInternalException e, String message) {
		JSchException jschEx = getJSchException(e);
		if (jschEx != null && jschEx instanceof HostFingerprintException) {
			HostFingerprintException cause = (HostFingerprintException) jschEx;
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, cause.getMessage(), addRepositoryInfo(cause.formJson()), cause);
		}
		//JSch handles auth fail by exception message
		if (jschEx != null && jschEx.getMessage() != null && jschEx.getMessage().toLowerCase(Locale.ENGLISH).contains("auth fail")) { //$NON-NLS-1$
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_UNAUTHORIZED, jschEx.getMessage(), addRepositoryInfo(new JSONObject()), jschEx);
		}

		//Log connection problems directly
		if (e.getCause() instanceof TransportException) {
			TransportException cause = (TransportException) e.getCause();
			if (matchMessage(JGitText.get().serviceNotPermitted, cause.getMessage())) {
				//Http connection problems are distinguished by exception message
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_FORBIDDEN, cause.getMessage(), addRepositoryInfo(new JSONObject()), cause);
			} else if (matchMessage(JGitText.get().notAuthorized, cause.getMessage())) {
				//Http connection problems are distinguished by exception message
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_UNAUTHORIZED, cause.getMessage(), addRepositoryInfo(new JSONObject()), cause);
			} else {
				//Other http connection problems reported directly
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, cause.getMessage() == null ? message : cause.getMessage(), addRepositoryInfo(new JSONObject()), cause);
			}

		}

		return new Status(IStatus.ERROR, GitActivator.PI_GIT, message, e.getCause() == null ? e : e.getCause());
	}

	public GitJob(String name, GitCredentialsProvider credentials) {
		super(name);
		this.credentials = credentials;
	}

	public GitJob(String name) {
		super(name);
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
}
