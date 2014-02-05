/*******************************************************************************
 * Copyright (c) 2010, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.servlets;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.MetadataInfo;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * A servlet for accessing and modifying preferences.
 * GET /prefs/ to return the preferences and children of the preference root node as a JSON object (the children of the root are the scopes)
 * GET /prefs/[path] returns the preferences and children of the given preference node as a JSON object
 * GET /prefs/[path]?key=[key] returns the value of the preference in the node at the given path, with the given key, as a JSON string
 * PUT /prefs/[path] sets all the preferences at the given path to the provided JSON object
 * PUT /prefs/[path]?key=[key]&value=[value] sets the value of the preference at the given path with the given key to the provided value
 * DELETE /prefs/[path] to delete an entire preference node
 * DELETE /prefs/[path]?key=[key] to delete a single preference at the given path with the given key
 */
public class PreferencesServlet extends OrionServlet {
	private static final long serialVersionUID = 1L;

	public PreferencesServlet() {
		super();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		MetadataInfo node = getNode(req, resp);
		if (node == null)
			return;
		String key = req.getParameter("key"); //$NON-NLS-1$
		try {
			String prefix = getPrefix(req);

			//if a key is specified get that single value, otherwise get the entire node
			JSONObject result = null;
			if (key != null) {
				prefix = prefix + '/' + key;
				String value = node.getProperty(prefix.toString());
				if (value == null) {
					handleNotFound(req, resp, HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				result = new JSONObject().put(key, value);
			} else {
				result = toJSON(req, prefix.toString(), node);
				//empty result should be treated as not found
				if (result.length() == 0) {
					handleNotFound(req, resp, HttpServletResponse.SC_NOT_FOUND);
					return;
				}
			}
			writeJSONResponse(req, resp, result);
		} catch (Exception e) {
			handleException(resp, NLS.bind("Failed to retrieve preferences for path {0}", req.getPathInfo()), e);
			return;
		}
	}

	/**
	 * Returns the prefix for the preference to be retrieved or manipulated.
	 */
	private String getPrefix(HttpServletRequest req) {
		String pathString = req.getPathInfo();
		if (pathString == null)
			pathString = ""; //$NON-NLS-1$
		IPath path = new Path(pathString);
		String scope = path.segment(0);
		if ("user".equalsIgnoreCase(scope)) { //$NON-NLS-1$
			//format is /user/prefix
			path = path.removeFirstSegments(1);
		} else if ("workspace".equalsIgnoreCase(scope)) { //$NON-NLS-1$
			//format is /workspace/{workspaceId}/prefix
			path = path.removeFirstSegments(2);
		} else if ("project".equalsIgnoreCase(scope)) { //$NON-NLS-1$
			//format is /project/{workspaceId}/{projectName}/prefix
			path = path.removeFirstSegments(3);
		}
		return path.toString();
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		MetadataInfo info = getNode(req, resp);
		if (info == null) {
			//should not fail on delete when resource doesn't exist
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return;

		}
		String key = req.getParameter("key");
		try {
			String prefix = getPrefix(req);
			//if a key is specified write that single value, otherwise write the entire node
			boolean changed = false;
			if (key != null) {
				prefix = prefix + '/' + key;
				changed = info.setProperty(prefix.toString(), null) != null;
			} else {
				//can't overwrite base user settings via preference servlet
				if (prefix.startsWith("user/")) {
					resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
				changed = removeMatchingProperties(info, prefix.toString());
			}
			if (changed)
				save(info);
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		} catch (Exception e) {
			handleException(resp, NLS.bind("Failed to retrieve preferences for path {0}", req.getPathInfo()), e);
			return;
		}
	}

	/**
	 * Returns the metadata object associated with this request. This method controls
	 * exactly what metadata objects are exposed via this service. If there is no matching
	 * metadata object for the request, this method handles the appropriate response
	 * and returns <code>null</code>.
	 * @param req
	 * @param resp
	 */
	private MetadataInfo getNode(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		String pathString = req.getPathInfo();
		if (pathString == null)
			pathString = ""; //$NON-NLS-1$
		IPath path = new Path(pathString);
		int segmentCount = path.segmentCount();
		String scope = path.segment(0);
		try {
			if ("user".equalsIgnoreCase(scope)) { //$NON-NLS-1$
				String username = req.getRemoteUser();
				if (username == null) {
					resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
					return null;
				}
				return OrionConfiguration.getMetaStore().readUser(username);
			} else if ("workspace".equalsIgnoreCase(scope) && segmentCount > 1) { //$NON-NLS-1$
				//format is /workspace/{workspaceId}
				return OrionConfiguration.getMetaStore().readWorkspace(path.segment(1));
			} else if ("project".equalsIgnoreCase(scope) && segmentCount > 2) { //$NON-NLS-1$
				//format is /project/{workspaceId}/{projectName}
				return OrionConfiguration.getMetaStore().readProject(path.segment(1), path.segment(2));
			}
		} catch (CoreException e) {
			handleException(resp, "Internal error obtaining preferences", e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return null;
		}
		//invalid prefix
		handleNotFound(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		return null;
	}

	private void handleNotFound(HttpServletRequest req, HttpServletResponse resp, int code) throws ServletException {
		String path = req.getPathInfo() == null ? "/" : req.getPathInfo();
		String msg = code == HttpServletResponse.SC_NOT_FOUND ? "No preferences found for path {0}" : "Invalid preference path {0}";
		handleException(resp, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, NLS.bind(msg, path)), code);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		MetadataInfo info = getNode(req, resp);
		if (info == null)
			return;
		String key = req.getParameter("key"); //$NON-NLS-1$
		String prefix = getPrefix(req);
		try {
			boolean changed = false;
			if (key != null) {
				prefix = prefix + '/' + key;
				String newValue = req.getParameter("value"); //$NON-NLS-1$
				String oldValue = info.setProperty(prefix.toString(), newValue);
				changed = !newValue.equals(oldValue);
			} else {
				JSONObject newNode = new JSONObject(new JSONTokener(req.getReader()));
				//can't overwrite base user settings via preference servlet
				if (prefix.startsWith("user/")) {
					resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
				//operations should not be removed by PUT
				if (!prefix.equals("operations")) {
					
					//clear existing values matching prefix
					changed |= removeMatchingProperties(info, prefix.toString());
				}
				for (Iterator<String> it = newNode.keys(); it.hasNext();) {
					key = it.next();
					String newValue = newNode.getString(key);
					String qualifiedKey = prefix + '/' + key;
					String oldValue = info.setProperty(qualifiedKey, newValue);
					changed |= !newValue.equals(oldValue);
				}
			}
			if (changed)
				save(info);
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		} catch (Exception e) {
			handleException(resp, NLS.bind("Failed to store preferences for {0}", req.getRequestURL()), e);
			return;
		}
	}

	private void save(MetadataInfo info) throws CoreException {
		IMetaStore store = OrionConfiguration.getMetaStore();
		if (info instanceof UserInfo) {
			store.updateUser((UserInfo) info);
		} else if (info instanceof WorkspaceInfo) {
			store.updateWorkspace((WorkspaceInfo) info);
		} else if (info instanceof ProjectInfo) {
			store.updateProject((ProjectInfo) info);
		}
	}

	private boolean removeMatchingProperties(MetadataInfo info, String prefix) {
		final Map<String, String> properties = info.getProperties();
		boolean changed = false;
		final Set<String> keySet = properties.keySet();
		//convert keys to array to avoid concurrent modification of set
		for (String key : keySet.toArray(new String[keySet.size()])) {
			if (key.startsWith(prefix)) {
				String previous = info.setProperty(key, null);
				if (previous != null)
					changed = true;
			}
		}
		return changed;
	}

	/**
	 * Serializes a preference node as a JSON object.
	 */
	private JSONObject toJSON(HttpServletRequest req, String prefix, MetadataInfo info) throws JSONException {
		JSONObject result = new JSONObject();
		final Map<String, String> properties = info.getProperties();
		//from client's perspective key is the part after prefix
		for (String key : properties.keySet()) {
			if (key.startsWith(prefix))
				result.put(key.substring(prefix.length() + 1), stringToJSON(properties.get(key)));
		}
		return result;
	}

	/**
	 * Converts a string representation of a JSON value into the appropriate object type.
	 * Possible return types are: String, JSONObject, JSONArray, Boolean, Integer, Long, Double
	 */
	private static Object stringToJSON(String input) {
		if (input == null)
			return null;
		Object result = null;
		//test if the value is a JSON object
		if (input.startsWith("{")) { //$NON-NLS-1$
			try {
				result = new JSONObject(input);
			} catch (JSONException e) {
				//treat as string
			}
		} else if (input.startsWith("[")) { //$NON-NLS-1$
			try {
				result = new JSONArray(input);
			} catch (JSONException e) {
				//treat as string
			}
		}
		if (result == null)
			result = JSONObject.stringToValue(input);
		return result;

	}
}
