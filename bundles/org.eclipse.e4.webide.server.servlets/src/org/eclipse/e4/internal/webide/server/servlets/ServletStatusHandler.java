package org.eclipse.e4.internal.webide.server.servlets;

/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Helper class for handling serialization of exception responses in servlets.
 */
public class ServletStatusHandler extends ServletResourceHandler<IStatus> {

	public static final String SEVERITY_CANCEL = "cancel"; //$NON-NLS-1$
	public static final String SEVERITY_INFO = "info"; //$NON-NLS-1$
	public static final String SEVERITY_OK = "ok"; //$NON-NLS-1$
	public static final String SEVERITY_WARNING = "warning"; //$NON-NLS-1$
	public static final String SEVERITY_ERROR = "error"; //$NON-NLS-1$

	/**
	 * A detailed human readable error message string.
	 */
	public static final String PROP_DETAILED_MESSAGE = "detailedMessage"; //$NON-NLS-1$
	/**
	 * A string representing the status severity. The value is one of the 
	 * <code>SEVERITY_*</code> constants defined in this class.
	 */
	public static final String PROP_SEVERITY = "severity"; //$NON-NLS-1$
	/**
	 * A high level error message string, suitable for display to a user.
	 */
	public static final String PROP_MESSAGE = "message"; //$NON-NLS-1$
	/**
	 * The integer HTTP response code.
	 */
	public static final String PROP_HTTP_CODE = "httpCode"; //$NON-NLS-1$

	/**
	 * An integer status code. The value is specific to the component returning
	 * the exception.
	 */
	public static final String PROP_CODE = "code"; //$NON-NLS-1$

	/**
	 * A property defining an optional status object indicating the cause
	 * of the exception.
	 */
	public static final String PROP_CAUSE = "cause"; //$NON-NLS-1$

	/**
	 * A property defining a URL of a page with further details about the
	 * exception and how it can be resolved.
	 */
	public static final String PROP_SEE_ALSO = "seeAlso"; //$NON-NLS-1$

	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, IStatus error) throws ServletException {
		int httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		if (error instanceof ServerStatus)
			httpCode = ((ServerStatus) error).getHttpCode();
		response.setStatus(httpCode);
		response.setContentType(ProtocolConstants.CONTENT_TYPE_JSON);
		try {
			response.getWriter().print(toJSON(error, httpCode).toString());
		} catch (IOException ioe) {
			//just throw a servlet exception
			throw new ServletException(error.getMessage(), error.getException());
		}
		return true;
	}

	public JSONObject toJSON(IStatus status, int httpCode) {
		JSONObject result = new JSONObject();
		try {
			result.put(PROP_HTTP_CODE, httpCode);
			result.put(PROP_CODE, status.getCode());
			result.put(PROP_MESSAGE, status.getMessage());
			result.put(PROP_SEVERITY, toSeverityString(status.getSeverity()));
			Throwable exception = status.getException();
			if (exception != null)
				result.put(PROP_DETAILED_MESSAGE, exception.getMessage());
			//Could also include "seeAlso" and "errorCause"
		} catch (JSONException e) {
			//can only happen if the key is null
		}
		return result;
	}

	private String toSeverityString(int severity) {
		//note the severity string should not be translated
		switch (severity) {
			case IStatus.ERROR :
				return SEVERITY_ERROR;
			case IStatus.WARNING :
				return SEVERITY_WARNING;
			case IStatus.INFO :
				return SEVERITY_INFO;
			case IStatus.CANCEL :
				return SEVERITY_CANCEL;
			case IStatus.OK :
				return SEVERITY_OK;
		}
		return null;
	}
}
