/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A status that also incorporates an HTTP response code. This status is suitable
 * for throwing an exception where the appropriate HTTP response for that failure
 * is specified.
 */
public class ServerStatus extends Status {
	/**
	 * A property defining an optional status object indicating the cause
	 * of the exception.
	 */
	//	private static final String PROP_CAUSE = "Cause"; //$NON-NLS-1$
	/**
	 * An integer status code. The value is specific to the component returning
	 * the exception.
	 */
	static final String PROP_CODE = "Code"; //$NON-NLS-1$
	/**
	 * A detailed human readable error message string.
	 */
	public static final String PROP_DETAILED_MESSAGE = "DetailedMessage"; //$NON-NLS-1$
	/**
	 * A detailed human readable error message string.
	 */
	public static final String PROP_CAUSE_MESSAGE = "CauseMessage"; //$NON-NLS-1$

	/**
	 * The id of the OSGi bundle where the error originated.
	 */
	public static final String PROP_BUNDLE_ID = "BundleId"; //$NON-NLS-1$
	/**
	 * The integer HTTP response code.
	 */
	static final String PROP_HTTP_CODE = "HttpCode"; //$NON-NLS-1$
	/**
	 * A high level error message string, suitable for display to a user.
	 */
	public static final String PROP_MESSAGE = "Message"; //$NON-NLS-1$

	/**
	 * A property containing JSON object with data needed to handle exception
	 */
	static final String JSON_DATA = "JsonData"; //$NON-NLS-1$

	/**
	 * A property defining a URL of a page with further details about the
	 * exception and how it can be resolved.
	 */
	//	private static final String PROP_SEE_ALSO = "SeeAlso"; //$NON-NLS-1$
	/**
	 * A string representing the status severity. The value is one of the 
	 * <code>SEVERITY_*</code> constants defined in this class.
	 */
	static final String PROP_SEVERITY = "Severity"; //$NON-NLS-1$
	static final String SEVERITY_CANCEL = "Cancel"; //$NON-NLS-1$
	static final String SEVERITY_ERROR = "Error"; //$NON-NLS-1$
	static final String SEVERITY_INFO = "Info"; //$NON-NLS-1$
	static final String SEVERITY_OK = "Ok"; //$NON-NLS-1$
	static final String SEVERITY_WARNING = "Warning"; //$NON-NLS-1$

	private int httpCode;
	private JSONObject jsonData;

	/**
	 * Converts a status into a server status.
	 */
	public static ServerStatus convert(IStatus status) {
		int httpCode = 200;
		if (status.getSeverity() == IStatus.ERROR || status.getSeverity() == IStatus.CANCEL)
			httpCode = 500;
		return convert(status, httpCode);
	}

	/**
	 * Converts a status into a server status.
	 */
	public static ServerStatus convert(IStatus status, int httpCode) {
		if (status instanceof ServerStatus)
			return (ServerStatus) status;
		return new ServerStatus(status, httpCode);
	}

	/**
	 * Returns a server status from a given JSON representation as produced
	 * by the {@link #toJSON()} method.
	 * @param string The string representation
	 * @return The status
	 * @throws JSONException If the provided string is not valid JSON representation of a status
	 */
	public static ServerStatus fromJSON(String string) throws JSONException {
		JSONObject object = new JSONObject(string);

		int httpCode = object.getInt(PROP_HTTP_CODE);
		int code = object.getInt(PROP_CODE);
		String message = object.getString(PROP_MESSAGE);
		int severity = fromSeverityString(object.getString(PROP_SEVERITY));
		String pluginId = object.optString(PROP_BUNDLE_ID);
		if (pluginId == null || pluginId.length() == 0)
			pluginId = ServerConstants.PI_SERVER_CORE;
		String detailMessage = object.optString(PROP_DETAILED_MESSAGE, null);
		Exception cause = detailMessage == null ? null : new Exception(detailMessage);
		JSONObject jsonData = object.optJSONObject(JSON_DATA);
		return new ServerStatus(new Status(severity, pluginId, code, message, cause), httpCode, jsonData);
	}

	public ServerStatus(int severity, int httpCode, String message, Throwable exception) {
		super(severity, ServerConstants.PI_SERVER_CORE, message, exception);
		this.httpCode = httpCode;
	}

	public ServerStatus(int severity, int httpCode, String message, JSONObject jsonData, Throwable exception) {
		super(severity, ServerConstants.PI_SERVER_CORE, message, exception);
		this.httpCode = httpCode;
		this.jsonData = jsonData;
	}

	public ServerStatus(IStatus status, int httpCode) {
		super(status.getSeverity(), status.getPlugin(), status.getCode(), status.getMessage(), status.getException());
		this.httpCode = httpCode;
	}

	public ServerStatus(IStatus status, int httpCode, JSONObject jsonData) {
		super(status.getSeverity(), status.getPlugin(), status.getCode(), status.getMessage(), status.getException());
		this.httpCode = httpCode;
		this.jsonData = jsonData;
	}

	/**
	 * Returns the HTTP response code associated with this status.
	 * @return the HTTP response code associated with this status.
	 */
	public int getHttpCode() {
		return httpCode;
	}

	/**
	 * Returns the JSON data associated with this status. May be 
	 * <code>null</code> if no additional data is available.
	 * @return the JSON data associated with this status.
	 */
	public JSONObject getJsonData() {
		return jsonData;
	}

	private String getSeverityString() {
		//note the severity string should not be translated
		switch (getSeverity()) {
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

	static int fromSeverityString(String s) {
		if (SEVERITY_ERROR.equals(s)) {
			return ERROR;
		} else if (SEVERITY_WARNING.equals(s)) {
			return WARNING;
		} else if (SEVERITY_INFO.equals(s)) {
			return INFO;
		} else if (SEVERITY_CANCEL.equals(s)) {
			return CANCEL;
		}
		return OK;
	}

	/**
	 * Returns a JSON representation of this status object. The resulting
	 * object can be converted back into a ServerStatus instance using the
	 * {@link #fromJSON(String)} factory method.
	 * @return A JSON representation of this status.
	 */
	public JSONObject toJSON() {
		JSONObject result = new JSONObject();
		try {
			result.put(PROP_HTTP_CODE, httpCode);
			result.put(PROP_CODE, getCode());
			result.put(PROP_MESSAGE, getMessage());
			result.put(PROP_BUNDLE_ID, getPlugin());
			result.put(PROP_SEVERITY, getSeverityString());
			if (jsonData != null)
				result.put(JSON_DATA, jsonData);
			Throwable exception = getException();
			if (exception != null)
				result.put(PROP_DETAILED_MESSAGE, exception.getMessage());
			if (exception != null && exception.getCause() != null)
				result.put(PROP_CAUSE_MESSAGE, exception.getCause().getMessage());
			//Could also include "SeeAlso" and "Cause"
		} catch (JSONException e) {
			//can only happen if the key is null
		}
		return result;
	}
}
