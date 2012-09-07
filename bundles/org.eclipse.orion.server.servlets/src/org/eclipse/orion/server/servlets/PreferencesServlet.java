/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.users.OrionScope;
import org.eclipse.osgi.util.NLS;
import org.json.*;
import org.osgi.service.prefs.BackingStoreException;

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
	private IEclipsePreferences prefRoot;

	public PreferencesServlet() {
		super();
	}

	@Override
	public void init() throws ServletException {
		super.init();
		prefRoot = new OrionScope().getNode(""); //$NON-NLS-1$
	}

	@Override
	public void destroy() {
		super.destroy();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);
		IEclipsePreferences node = getNode(req, resp, false);
		if (node == null)
			return;
		String key = req.getParameter("key");
		try {
			//if a key is specified write that single value, otherwise write the entire node
			JSONObject result = null;
			if (key != null) {
				String value = node.get(key, null);
				if (value == null) {
					handleNotFound(req, resp, HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				result = new JSONObject().put(key, value);
			} else
				result = toJSON(req, node);
			writeJSONResponse(req, resp, result);
		} catch (Exception e) {
			handleException(resp, NLS.bind("Failed to retrieve preferences for path {0}", req.getPathInfo()), e);
			return;
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		IEclipsePreferences node = getNode(req, resp, false);
		if (node == null) {
			//should not fail on delete when resource doesn't exist
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return;

		}
		String key = req.getParameter("key");
		try {
			//if a key is specified write that single value, otherwise write the entire node
			if (key != null)
				node.remove(key);
			else
				node.removeNode();
			prefRoot.flush();
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		} catch (Exception e) {
			handleException(resp, NLS.bind("Failed to retrieve preferences for path {0}", req.getPathInfo()), e);
			return;
		}
	}

	/**
	 * Returns the preference node associated with this request. This method controls
	 * exactly what preference nodes are exposed via this service. If there is no matching
	 * preference node for the request, this method handles the appropriate response
	 * and returns <code>null</code>.
	 * @param req
	 * @param resp
	 * @param create If <code>true</code>, the node will be created if it does not already exist. If
	 * <code>false</code>, this method sets the response status to 404 and returns null.
	 */
	private IEclipsePreferences getNode(HttpServletRequest req, HttpServletResponse resp, boolean create) throws ServletException {
		if (prefRoot == null) {
			handleException(resp, "Unable to obtain preference service", null);
			return null;
		}
		String pathString = req.getPathInfo();
		if (pathString == null)
			pathString = ""; //$NON-NLS-1$
		IPath path = new Path(pathString);
		int segmentCount = path.segmentCount();
		String scope = path.segment(0);
		//note that the preference service API scope names don't match those used in our persistence layer.
		IPath nodePath = null;
		if ("user".equalsIgnoreCase(scope)) { //$NON-NLS-1$
			String username = req.getRemoteUser();
			if (username == null) {
				resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
				return null;
			}
			nodePath = new Path("Users").append(username); //$NON-NLS-1$
		} else if ("workspace".equalsIgnoreCase(scope) && segmentCount > 1) { //$NON-NLS-1$
			nodePath = new Path("Workspaces"); //$NON-NLS-1$
		} else if ("project".equalsIgnoreCase(scope) && segmentCount > 1) { //$NON-NLS-1$
			nodePath = new Path("Projects"); //$NON-NLS-1$
		} else {
			//invalid prefix
			handleNotFound(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			return null;
		}
		//we allow arbitrary subtrees beneath our three supported roots
		if (nodePath != null) {
			String childPath = nodePath.append(path.removeFirstSegments(1)).toString();
			try {
				if (create || prefRoot.nodeExists(childPath))
					return (IEclipsePreferences) prefRoot.node(childPath);
			} catch (BackingStoreException e) {
				String msg = NLS.bind("Error retrieving preferences for path {0}", pathString);
				handleException(resp, new Status(IStatus.ERROR, Activator.PI_SERVER_SERVLETS, msg), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return null;
			}
		}
		handleNotFound(req, resp, HttpServletResponse.SC_NOT_FOUND);
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
		IEclipsePreferences node = getNode(req, resp, true);
		if (node == null)
			return;
		String key = req.getParameter("key");
		try {
			if (key != null) {
				node.put(key, req.getParameter("value"));
			} else {
				JSONObject newNode = new JSONObject(new JSONTokener(req.getReader()));
				node.clear();
				for (Iterator<String> it = newNode.keys(); it.hasNext();) {
					key = it.next();
					node.put(key, newNode.getString(key));
				}
			}
			prefRoot.flush();
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		} catch (Exception e) {
			handleException(resp, NLS.bind("Failed to store preferences for {0}", req.getRequestURL()), e);
			return;
		}
	}

	/**
	 * Serializes a preference node as a JSON object.
	 */
	private JSONObject toJSON(HttpServletRequest req, IEclipsePreferences node) throws JSONException, BackingStoreException {
		JSONObject result = new JSONObject();
		//TODO Do we need this extra information?
		//		String nodePath = node.absolutePath();
		//		result.put("path", nodePath);
		//		JSONObject children = new JSONObject();
		//		for (String child : node.childrenNames())
		//			children.put(child, createQuery(req, "/prefs" + nodePath + '/' + child));
		//		result.put("children", children);
		//		JSONObject values = new JSONObject();
		for (String key : node.keys()) {
			String valueString = node.get(key, null);
			Object value = null;
			if (valueString != null) {
				try {
					//value might be serialized JSON
					value = new JSONObject(valueString);
				} catch (JSONException e) {
					//value must be a string
					value = valueString;
				}
			}
			result.put(key, value);
		}
		return result;
	}
}
