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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.util.FS;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToConfigEntryConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.osgi.util.NLS;
import org.json.*;

/**
 * A handler for Git Clone operation.
 */
public class GitConfigHandlerV1 extends ServletResourceHandler<String> {

	private ServletResourceHandler<IStatus> statusHandler;

	GitConfigHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		try {
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, path);
				case POST :
					return handlePost(request, response, path);
				case PUT :
					return handlePut(request, response, path);
				case DELETE :
					return handleDelete(request, response, path);
			}
		} catch (Exception e) {
			String msg = NLS.bind("Failed to process an operation on commits for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, String path) throws IOException, JSONException, ServletException, URISyntaxException, CoreException, ConfigInvalidException {
		Path p = new Path(path);
		if (p.segment(0).equals("clone") && p.segment(1).equals("file")) { //$NON-NLS-1$ //$NON-NLS-2$
			// expected path /gitapi/config/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
			FileBasedConfig config = getLocalConfig(gitDir);
			URI baseLocation = getURI(request);

			JSONObject result = configToJSON(config, baseLocation);
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		} else if (p.segment(1).equals("clone") && p.segment(2).equals("file")) { //$NON-NLS-1$ //$NON-NLS-2$
			// expected path /gitapi/config/{key}/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			Repository db = new FileRepository(gitDir);
			StoredConfig config = db.getConfig();
			URI baseLocation = getURI(request);

			String key = p.segment(0);
			String[] keySegments = keyToSegments(key);
			if (keySegments == null)
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Config entry key must be provided in the following form: section[.subsection].name", null));
			if (!variableExist(config, keySegments))
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "There is no config entry with key provided", null));

			JSONObject result = configEntryToJSON(config, keySegments, baseLocation);
			OrionServlet.writeJSONResponse(request, response, result);
			return true;
		}
		return false;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, JSONException, ServletException, URISyntaxException, ConfigInvalidException {
		Path p = new Path(path);
		if (p.segment(0).equals("clone") && p.segment(1).equals("file")) { //$NON-NLS-1$ //$NON-NLS-2$
			// expected path /gitapi/config/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(1));
			FileBasedConfig config = getLocalConfig(gitDir);
			URI baseLocation = getURI(request);

			JSONObject toPost = OrionServlet.readJSONRequest(request);
			String key = toPost.optString(GitConstants.KEY_CONFIG_ENTRY_KEY, null);
			if (key == null || key.isEmpty())
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Confign entry key must be provided", null));
			String value = toPost.optString(GitConstants.KEY_CONFIG_ENTRY_VALUE, null);
			if (value == null || value.isEmpty())
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Confign entry value must be provided", null));
			String[] keySegments = keyToSegments(key);
			if (keySegments == null)
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Config entry key must be provided in the following form: section[.subsection].name", null));

			// determine if config entry is already present
			boolean present = variableExist(config, keySegments);

			// set new value
			config.setString(keySegments[0], keySegments[1], keySegments[2], value);
			config.save();

			JSONObject result = configEntryToJSON(config, keySegments, baseLocation);
			OrionServlet.writeJSONResponse(request, response, result);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, result.getString(ProtocolConstants.KEY_LOCATION));
			if (!present)
				response.setStatus(HttpServletResponse.SC_CREATED);
			return true;
		}
		return false;
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, JSONException, ServletException, URISyntaxException, ConfigInvalidException {
		Path p = new Path(path);
		if (p.segment(1).equals("clone") && p.segment(2).equals("file")) { //$NON-NLS-1$ //$NON-NLS-2$
			// expected path /gitapi/config/{key}/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			FileBasedConfig config = getLocalConfig(gitDir);
			URI baseLocation = getURI(request);

			String key = p.segment(0);
			if (key == null || key.isEmpty())
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Confign entry key must be provided in URI", null));

			JSONObject toPut = OrionServlet.readJSONRequest(request);
			String value = toPut.optString(GitConstants.KEY_CONFIG_ENTRY_VALUE, null);
			if (value == null || value.isEmpty())
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Confign entry value must be provided", null));

			String[] keySegments = keyToSegments(key);
			if (keySegments == null)
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Config entry key must be provided in the following form: section[.subsection].name", null));

			// PUT allows only to modify existing config entries
			if (!variableExist(config, keySegments)) {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				return true;
			}

			// set new value
			config.setString(keySegments[0], keySegments[1], keySegments[2], value);
			config.save();

			JSONObject result = configEntryToJSON(config, keySegments, baseLocation);
			OrionServlet.writeJSONResponse(request, response, result);
			response.setHeader(ProtocolConstants.HEADER_LOCATION, result.getString(ProtocolConstants.KEY_LOCATION));
			return true;
		}
		return false;
	}

	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, String path) throws CoreException, IOException, ServletException, ConfigInvalidException {
		Path p = new Path(path);
		if (p.segment(1).equals("clone") && p.segment(2).equals("file")) { //$NON-NLS-1$ //$NON-NLS-2$
			// expected path /gitapi/config/{key}/clone/file/{path}
			File gitDir = GitUtils.getGitDir(p.removeFirstSegments(2));
			FileBasedConfig config = getLocalConfig(gitDir);

			String key = p.segment(0);
			String[] keySegments = keyToSegments(key);
			if (keySegments == null)
				return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Config entry key must be provided in the following form: section[.subsection].name", null));

			// determine if config entry exist
			if (variableExist(config, keySegments)) {
				// delete
				config.unset(keySegments[0], keySegments[1], keySegments[2]);
				config.save();

				response.setStatus(HttpServletResponse.SC_OK);
			} else {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
			return true;
		}
		return false;
	}

	// retrieves local config without any base config
	private FileBasedConfig getLocalConfig(File gitDirectory) throws IOException, ConfigInvalidException {
		FileRepository db = new FileRepository(gitDirectory);
		FileBasedConfig config = new FileBasedConfig(db.getConfig().getFile(), FS.detect());
		config.load();
		return config;
	}

	// converts whole config to JSON representation
	JSONObject configToJSON(Config config, URI baseLocation) throws JSONException, URISyntaxException {
		JSONObject result = new JSONObject();
		JSONArray children = new JSONArray();
		for (String section : config.getSections()) {
			// proceed configuration entries: section.name
			for (String name : config.getNames(section))
				children.put(configEntryToJSON(config, new String[] {section, null, name}, baseLocation));
			// proceed configuration entries: section.subsection.name
			for (String subsection : config.getSubsections(section))
				for (String name : config.getNames(section, subsection))
					children.put(configEntryToJSON(config, new String[] {section, subsection, name}, baseLocation));
		}
		result.put(ProtocolConstants.KEY_CHILDREN, children);
		return result;
	}

	// reads configuration entry and converts it to JSON representation
	JSONObject configEntryToJSON(Config config, String[] keySegments, URI baseLocation) throws JSONException, URISyntaxException {
		String value = config.getString(keySegments[0], keySegments[1], keySegments[2]);
		if (value == null)
			value = "";

		String key = segmentsToKey(keySegments);
		JSONObject result = new JSONObject();
		result.put(GitConstants.KEY_CONFIG_ENTRY_KEY, key);
		result.put(GitConstants.KEY_CONFIG_ENTRY_VALUE, value);
		result.put(ProtocolConstants.KEY_LOCATION, BaseToConfigEntryConverter.REMOVE_FIRST_2.baseToConfigEntryLocation(baseLocation, key));
		return result;
	}

	// checks if given variable exist in configuration
	boolean variableExist(Config config, String[] keySegments) {
		if (keySegments[1] != null && !config.getNames(keySegments[0], keySegments[1]).contains(keySegments[2]))
			return false;
		else if (keySegments[1] == null && !config.getNames(keySegments[0]).contains(keySegments[2]))
			return false;

		return true;
	}

	// converts segments of key to key representation
	// returns null if input array is invalid
	private String segmentsToKey(String[] segments) {
		if (segments.length == 3)
			// check if there is subsection part
			return segments[1] == null ? String.format("%s.%s", segments[0], segments[2]) : String.format("%s.%s.%s", segments[0], segments[1], segments[2]);

		return null;
	}

	// converts key representation to key segments
	// returns null if input string is invalid
	private String[] keyToSegments(String key) {
		String[] keyParts = key.split("\\.");
		if (keyParts.length == 3)
			// section, subsection and name provided
			return keyParts;
		else if (keyParts.length == 2)
			// no subsection provided
			return keyParts = new String[] {keyParts[0], null, keyParts[1]};

		return null;
	}
}
