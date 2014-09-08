/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.servlets;

import java.io.InputStreamReader;
import java.net.URLDecoder;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.objects.CFObject;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract REST object handler. The class contains a set of default helper methods
 * for common functions, e. g. resource extraction, error handing, etc.
 */
public abstract class AbstractRESTHandler<T extends CFObject> extends ServletResourceHandler<String> {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$

	protected ServletResourceHandler<IStatus> statusHandler;

	public AbstractRESTHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	/**
	 * Builds the handled resource according to custom criteria.
	 * @param request The request object used to build the resource.
	 * @param path Path suffix required to handle the request.
	 * @throws CoreException if the resource cannot be constructed due to an internal server error.
	 * @return <code>null</code> if the resource cannot be constructed due to invalid request, the resource object otherwise.
	 */
	protected abstract T buildResource(HttpServletRequest request, String path) throws CoreException;

	/**
	 * Helper method for PUT data extraction.
	 * @param request The PUT requested to be processed.
	 * @return The extracted data JSON or null if none provided or invalid JSON format.
	 */
	protected JSONObject extractJSONData(final HttpServletRequest request) {
		try {
			JSONTokener tokener = new JSONTokener(new InputStreamReader(request.getInputStream()));
			return new JSONObject(tokener);
		} catch (Exception ex) {
			return null;
		}
	}

	protected JSONObject extractJSONData(final String param) {
		try {
			return param != null ? new JSONObject(URLDecoder.decode(param, "UTF8")) : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Handles a GET request. Note this method is meant to be overridden in descendant classes.
	 * @param request The GET request being handled.
	 * @param response The response associated with the request.
	 * @param path Path suffix required to handle the request.
	 * @return A {@link JazzJob} which returns the requested resource on completion.
	 */
	protected CFJob handleGet(T resource, final HttpServletRequest request, final HttpServletResponse response, final String path) {
		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_IMPLEMENTED, msg, null);
			}
		};
	}

	/**
	 * Handles an idempotent PUT request. Note this method is meant to be overridden in descendant classes.
	 * @param request The PUT request being handled.
	 * @param response The response associated with the request.
	 * @param path Path suffix required to handle the request.
	 * @return A {@link JazzJob} which returns the PUT request result on completion. 
	 */
	protected CFJob handlePut(T resource, final HttpServletRequest request, final HttpServletResponse response, final String path) {
		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_IMPLEMENTED, msg, null);
			}
		};
	}

	/**
	 * Handles a POST request. Note this method is meant to be overridden in descendant classes.
	 * The POST request is not idempotent as PUT, although it may handle similar operations.
	 * @param request The POST request being handled.
	 * @param response The response associated with the request.
	 * @param path Path suffix required to handle the request.
	 * @return A {@link JazzJob} which returns the POST request result on completion.
	 */
	protected CFJob handlePost(final T resource, final HttpServletRequest request, final HttpServletResponse response, final String path) {
		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_IMPLEMENTED, msg, null);
			}
		};
	}

	/**
	 * Handles a DELETE request. Note this method is meant to be overridden in descendant classes.
	 * @param request The DELETE request being handled.
	 * @param response The response associated with the request.
	 * @param path Path suffix required to handle the request.
	 * @return A {@link JazzJob} which returns the DELETE request result on completion.
	 */
	protected CFJob handleDelete(T resource, final HttpServletRequest request, final HttpServletResponse response, final String path) {
		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_IMPLEMENTED, msg, null);
			}
		};
	}

	/**
	 * A helper method which handles returning a 404 HTTP error status.
	 * @param request The request being handled.
	 * @param response The response associated with the request.
	 * @param path Path mapped to the handled request. Used to display the error message.
	 * @return <code>true</code> iff the 404 has been sent, <code>false</code> otherwise.
	 */
	protected boolean handleNotExistingResource(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
		ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, msg, null);
		return statusHandler.handleRequest(request, response, status);
	}

	/**
	 * A helper method which handles returning a 409 HTTP error status.
	 * @param request The request being handled.
	 * @param response The response associated with the request.
	 * @param path Path mapped to the handled request. Used to display the error message.
	 * @return <code>true</code> iff the 409 has been sent, <code>false</code> otherwise.
	 */
	protected boolean handleConflictingResource(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
		ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_CONFLICT, msg, null);
		return statusHandler.handleRequest(request, response, status);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		try {
			/* build the request resource */
			T resource = buildResource(request, path);

			switch (getMethod(request)) {
				case GET :
					CFJob getJob = handleGet(resource, request, response, path);
					return TaskJobHandler.handleTaskJob(request, response, getJob, statusHandler, JsonURIUnqualificationStrategy.LOCATION_ONLY);

				case PUT :
					CFJob putJob = handlePut(resource, request, response, path);
					return TaskJobHandler.handleTaskJob(request, response, putJob, statusHandler, JsonURIUnqualificationStrategy.LOCATION_ONLY);

				case POST :
					CFJob postJob = handlePost(resource, request, response, path);
					return TaskJobHandler.handleTaskJob(request, response, postJob, statusHandler, JsonURIUnqualificationStrategy.LOCATION_ONLY);

				case DELETE :
					CFJob deleteJob = handleDelete(resource, request, response, path);
					return TaskJobHandler.handleTaskJob(request, response, deleteJob, statusHandler, JsonURIUnqualificationStrategy.LOCATION_ONLY);

				default :
					/* we don't know how to handle this request */
					return false;
			}
		} catch (Exception e) {
			String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
			ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
			logger.error(msg, e);
			return statusHandler.handleRequest(request, response, status);
		}
	}
}
