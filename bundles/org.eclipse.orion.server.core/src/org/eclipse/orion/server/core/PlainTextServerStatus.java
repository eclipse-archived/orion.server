package org.eclipse.orion.server.core;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.json.JSONException;
import org.json.JSONObject;
/**
 * This is a regular server status except that it returns JsonData as String. This is a walkaround for Defect 90007 
 *
 */
public class PlainTextServerStatus extends ServerStatus {

	static final String PLAIN_TEXT_STATUS = "PlainTextStatus"; //$NON-NLS-1$

	/**
	 * @param severity
	 * @param httpCode
	 * @param message
	 * @param jsonData
	 * @param exception
	 */
	public PlainTextServerStatus(int severity, int httpCode, String message, JSONObject jsonData, Throwable exception) {
		super(severity, httpCode, message, jsonData, exception);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param severity
	 * @param httpCode
	 * @param message
	 * @param exception
	 */
	public PlainTextServerStatus(int severity, int httpCode, String message, Throwable exception) {
		super(severity, httpCode, message, exception);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param status
	 * @param httpCode
	 * @param jsonData
	 */
	public PlainTextServerStatus(IStatus status, int httpCode, JSONObject jsonData) {
		super(status, httpCode, jsonData);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param status
	 * @param httpCode
	 */
	public PlainTextServerStatus(IStatus status, int httpCode) {
		super(status, httpCode);
		// TODO Auto-generated constructor stub
	}

	public PlainTextServerStatus(ServerStatus serverStatus) {
		super(new Status(serverStatus.getSeverity(), ServerConstants.PI_SERVER_CORE, serverStatus.getCode(), serverStatus.getMessage(), serverStatus.getException()), serverStatus.getHttpCode(), serverStatus.getJsonData());
	}

	public static PlainTextServerStatus fromJSON(String string) throws JSONException {
		JSONObject object = new JSONObject(string);
		int httpCode = object.getInt(PROP_HTTP_CODE);
		int code = object.getInt(PROP_CODE);
		String message = object.getString(PROP_MESSAGE);
		int severity = fromSeverityString(object.getString(PROP_SEVERITY));
		String detailMessage = object.optString(PROP_DETAILED_MESSAGE, null);
		Exception cause = detailMessage == null ? null : new Exception(detailMessage);
		String jsonDataString = object.getString(JSON_DATA);
		JSONObject jsonData = new JSONObject(jsonDataString);
		return new PlainTextServerStatus(new Status(severity, ServerConstants.PI_SERVER_CORE, code, message, cause), httpCode, jsonData);
	}

	@Override
	public JSONObject toJSON() {
		JSONObject json = super.toJSON();
		try {
			if (json.has(JSON_DATA)) {
				json.put(JSON_DATA, json.getJSONObject(JSON_DATA).toString());
			}
			json.put(PLAIN_TEXT_STATUS, true);
		} catch (JSONException e) {
			//ignore
		}
		return json;
	}

}
