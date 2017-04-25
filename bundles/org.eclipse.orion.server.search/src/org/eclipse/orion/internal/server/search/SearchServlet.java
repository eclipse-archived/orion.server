/*******************************************************************************
 * Copyright (c) 2014, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.core.users.UserConstants;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet for performing searches against files in the workspace.
 * 
 * @author Aidan Redpath
 * @author Anthony Hunter
 */
public class SearchServlet extends OrionServlet {

	private static final String FIELD_NAMES = "Name,NameLower,Length,Directory,LastModified,Location,Path,RegEx,CaseSensitive,WholeWord,Exclude"; //$NON-NLS-1$

	private static final List<String> FIELD_LIST = Arrays.asList(FIELD_NAMES.split(",")); //$NON-NLS-1$

	private static final long serialVersionUID = 1L;

	private Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$

	private void addAllProjectsToScope(WorkspaceInfo workspaceInfo, SearchOptions options) throws CoreException {
		List<String> projectnames = workspaceInfo.getProjectNames();
		for (String projectName : projectnames) {
			ProjectInfo projectInfo = OrionConfiguration.getMetaStore().readProject(workspaceInfo.getUniqueId(), projectName);
			if (projectInfo == null) {
				logger.error("Unexpected missing project with name " + projectName + " in workspace " + workspaceInfo.getUniqueId());
				continue;
			}
			SearchScope scope = new SearchScope(projectInfo.getProjectStore(), workspaceInfo, projectInfo);
			options.getScopes().add(scope);
		}
	}

	private SearchOptions buildSearchOptions(HttpServletRequest req, HttpServletResponse resp) throws SearchException, ServletException {
		SearchOptions options = new SearchOptions();

		String queryString = getEncodedParameter(req, "q");
		if (queryString == null)
			return null;
		if (queryString.length() > 0) {
			//divide into search terms delimited by space or plus ('+') character
			List<String> terms = new ArrayList<String>(Arrays.asList(queryString.split("[\\s\\+]+"))); //$NON-NLS-1$
			while (!terms.isEmpty()) {
				String term = terms.remove(0);
				if (term.length() == 0)
					continue;
				if (isSearchField(term)) {
					if (term.startsWith("NameLower:")) { //$NON-NLS-1$
						//decode the search term, we do not want to decode the location
						try {
							term = URLDecoder.decode(term, "UTF-8"); //$NON-NLS-1$
						} catch (UnsupportedEncodingException e) {
							//try with encoded term
						}
						options.setIsFilenamePatternCaseSensitive(false);
						options.setFilenamePattern(term.substring(10));
					} else if (term.startsWith("Location:")) { //$NON-NLS-1${
						int ctxLength = req.getContextPath().length();
						if((ctxLength + 9) > term.length()){
							handleException(resp, "Invalid search term: " + term, null, HttpServletResponse.SC_BAD_REQUEST);
							return null;
						}
						String location = term.substring(9 + ctxLength);
						try {
							location = URLDecoder.decode(location, "UTF-8"); //$NON-NLS-1$
						} catch (UnsupportedEncodingException e) {
							//try with encoded term
						}
						options.setLocation(location);
						continue;
					} else if (term.startsWith("Name:")) { //$NON-NLS-1$
						try {
							term = URLDecoder.decode(term, "UTF-8"); //$NON-NLS-1$
						} catch (UnsupportedEncodingException e) {
							//try with encoded term
						}
						options.setIsFilenamePatternCaseSensitive(true);
						options.setFilenamePattern(term.substring(5));
					} else if (term.startsWith("RegEx:")) {
						options.setRegEx(true);
					} else if (term.startsWith("CaseSensitive:")) {
						options.setIsSearchTermCaseSensitive(true);
					} else if (term.startsWith("WholeWord:")) {
						options.setIsSearchWholeWord(true);
					} else if(term.startsWith("Exclude:")) {
						String exclude = term.substring("Exclude:".length());
						String[] items = exclude.split(",");
						for(String item : items) {
							try {
								options.setExcluded(URLDecoder.decode(item, "UTF-8"));
							}
							catch(UnsupportedEncodingException usee) {
								//ignore, bad term
							}
						}
					}
				} else if(term.indexOf(":") > -1) {
					//unknown search term, ignore
					continue;
				} else {
					//decode the term string now
					try {
						term = URLDecoder.decode(term, "UTF-8"); //$NON-NLS-1$
					} catch (UnsupportedEncodingException e) {
						//try with encoded term
					}
					options.setSearchTerm(term);
					options.setFileSearch(true);
				}
			}
		}

		String login = req.getRemoteUser();
		options.setUsername(login);

		setScopes(req, resp, options);

		return options;
	}

	/**
	 * Convert the list of file results to JSON format for return to the client.
	 * @param contextPath the context path of the server that is added to the location for each result.
	 * @param files The list of files matching the search query.
	 * @param options The search options.
	 * @return the file results in JSON format.
	 */
	private JSONObject convertListToJson(String contextPath, List<SearchResult> files, SearchOptions options) {
		JSONObject resultsJSON = new JSONObject();
		JSONObject responseJSON = new JSONObject();
		try {
			resultsJSON.put("start", 0);
			JSONArray docs = new JSONArray();
			int found = 0;
			if(files != null) {
				found = files.size();
				for (SearchResult file : files) {
					docs.put(file.toJSON(contextPath));
				}
			}
			resultsJSON.put("numFound", found);
			resultsJSON.put("docs", docs);
			// Add to parent JSON
			JSONObject responseHeader = new JSONObject();
			responseHeader.put("status", 0);
			//responseHeader.put("QTime", 77);
			JSONObject params = new JSONObject();
			params.put("wt", "json");
			params.put("fl", FIELD_NAMES);
			JSONArray fq = new JSONArray();
			if (options.getDefaultLocation() != null) {
				fq.put("Location:" + options.getDefaultLocation());
			} else if (options.getLocation() != null) {
				fq.put("Location:" + options.getLocation());
			} else {
				throw new RuntimeException("Scope or DefaultScope is missing");
			}
			if (options.getUsername() != null) {
				fq.put("UserName:" + options.getUsername());
			} else {
				throw new RuntimeException("UserName is missing");
			}
			params.put("fq", fq);
			params.put("rows", "10000");
			params.put("start", "0");
			params.put("sort", "Path asc");
			responseHeader.put("params", params);
			responseJSON.put("responseHeader", responseHeader);
			responseJSON.put("response", resultsJSON);
		} catch (JSONException e) {
			logger.error("SearchServlet.convertListToJson: " + e.getLocalizedMessage(), e);
		} catch (CoreException e) {
			logger.error("SearchServlet.convertListToJson: " + e.getLocalizedMessage(), e);
		} catch (URISyntaxException e) {
			logger.error("SearchServlet.convertListToJson: " + e.getLocalizedMessage(), e);
		}
		return responseJSON;
	}

	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			if (SearchJob.isSearchJobRunning(req.getRemoteUser())) {
				resp.sendError(HttpServletResponse.SC_CONFLICT, "A search task is already running for " + req.getRemoteUser() + ", try again later.");
				return;
			}
			SearchOptions options = buildSearchOptions(req, resp);
			if(options == null) {
				return;
			}
			SearchJob searchJob = new SearchJob(options);
			searchJob.schedule();
			searchJob.join();
			if (!searchJob.getResult().isOK()) {
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, searchJob.getResult().getMessage());
			}
			List<SearchResult> files = searchJob.getSearchResults();
			writeResponse(req, resp, files, options);
		} catch (SearchException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} catch (InterruptedException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	/**
	 * Returns a request parameter in encoded form. Returns <code>null</code>
	 * if no such parameter is defined or has an empty value.
	 */
	private String getEncodedParameter(HttpServletRequest req, String key) {
		String query = req.getQueryString();
		for (String param : query.split("&")) { //$NON-NLS-1$
			String[] pair = param.split("=", 2); //$NON-NLS-1$
			if (pair.length == 2 && key.equals(pair[0]))
				return pair[1];
		}
		return null;
	}

	/**
	 * Returns whether the search term is against a particular field rather than the default field
	 * (search on name, location, etc).
	 */
	private boolean isSearchField(String term) {
		for (String field : FIELD_LIST) {
			if (term.startsWith(field + ":")) //$NON-NLS-1$
				return true;
		}
		return false;
	}

	/**
	 * Sets the default scopes to the location of each project.
	 * @param req The request from the servlet.
	 * @param res The response to the servlet.
	 * @throws SearchException Thrown if there is an error reading a file.
	 */
	private void setDefaultScopes(HttpServletRequest req, HttpServletResponse resp, SearchOptions options) throws SearchException {
		String login = req.getRemoteUser();
		try {
			UserInfo userInfo = OrionConfiguration.getMetaStore().readUserByProperty(UserConstants.USER_NAME, login, false, false);
			List<String> workspaceIds = userInfo.getWorkspaceIds();
			for (String workspaceId : workspaceIds) {
				WorkspaceInfo workspaceInfo = OrionConfiguration.getMetaStore().readWorkspace(workspaceId);
				options.setDefaultLocation("/file/" + workspaceId);
				addAllProjectsToScope(workspaceInfo, options);
			}
		} catch (CoreException e) {
			throw (new SearchException(e));
		}
	}

	private boolean setScopeFromRequest(HttpServletRequest req, HttpServletResponse resp, SearchOptions options) {
		try {
			String pathInfo = options.getLocation();
			if (pathInfo != null && (pathInfo.startsWith(Activator.LOCATION_FILE_SERVLET))) {
				pathInfo = pathInfo.substring(Activator.LOCATION_FILE_SERVLET.length());
			} else if (pathInfo != null && (pathInfo.startsWith(Activator.LOCATION_WORKSPACE_SERVLET))) {
				pathInfo = pathInfo.substring(Activator.LOCATION_WORKSPACE_SERVLET.length());
			}
			if (pathInfo != null && pathInfo.endsWith("*")) {
				pathInfo = pathInfo.substring(0, pathInfo.length() - 1);
			}
			IPath path = pathInfo == null ? Path.ROOT : new Path(pathInfo);
			// prevent path canonicalization hacks
			if (pathInfo != null && !pathInfo.equals(path.toString())) {
				return false;
			}
			// don't allow anyone to mess with metadata
			if (path.segmentCount() > 0 && ".metadata".equals(path.segment(0))) { //$NON-NLS-1$
				return false;
			}
			// Must have a path
			if (path.segmentCount() == 0) {
				return false;
			}

			WorkspaceInfo workspaceInfo = OrionConfiguration.getMetaStore().readWorkspace(path.segment(0));
			if (workspaceInfo == null || workspaceInfo.getUniqueId() == null) {
				return false;
			}
			if (path.segmentCount() == 1) {
				// Bug 415700: handle path format /workspaceId 
				if (workspaceInfo != null && workspaceInfo.getUniqueId() != null) {
					addAllProjectsToScope(workspaceInfo, options);
					return true;
				}

				return false;
			}
			//path format is /workspaceId/projectName/[suffix]
			ProjectInfo projectInfo = OrionConfiguration.getMetaStore().readProject(workspaceInfo.getUniqueId(), path.segment(1));
			if (projectInfo != null) {
				IFileStore projectStore = projectInfo.getProjectStore();
				IFileStore scopeStore = projectStore.getFileStore(path.removeFirstSegments(2));
				SearchScope scope = new SearchScope(scopeStore, workspaceInfo, projectInfo);
				options.getScopes().add(scope);
				return true;
			}
			// Bug 415700: handle path format /workspaceId/[file] 
			if (path.segmentCount() == 2) {
				IFileStore workspaceStore = OrionConfiguration.getMetaStore().getWorkspaceContentLocation(workspaceInfo.getUniqueId());
				IFileStore scopeStore = workspaceStore.getChild(path.segment(1));
				SearchScope scope = new SearchScope(scopeStore, workspaceInfo, null);
				options.getScopes().add(scope);
				return true;
			}

			return false;
		} catch (CoreException e) {
			logger.error("FileGrepper.setScopeFromRequest: " + e.getLocalizedMessage(), e);
			return false;
		}
	}

	/**
	 * Set the scope of the search to the user home if the scope was not given.
	 * @param req The HTTP request to the servlet.
	 * @param resp The HTTP response from the servlet.
	 * @throws SearchException 
	 */
	private void setScopes(HttpServletRequest req, HttpServletResponse resp, SearchOptions options) throws SearchException {
		if (!setScopeFromRequest(req, resp, options)) {
			setDefaultScopes(req, resp, options);
		}
	}

	private void writeResponse(HttpServletRequest req, HttpServletResponse resp, List<SearchResult> files, SearchOptions options) throws IOException {
		try {
			JSONObject json = convertListToJson(req.getContextPath(), files, options);
			writeJSONResponse(req, resp, json);
		} catch (IllegalStateException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
}
