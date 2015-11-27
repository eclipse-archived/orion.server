/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.servlets;

import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.jobs.ListPullRequestsJob;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONObject;

public class GitPullRequestHandlerV1 extends AbstractGitHandler {

	GitPullRequestHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		JSONObject credentials = requestInfo.getJSONRequest();
		try {

			String username = credentials.optString(GitConstants.KEY_USERNAME, "");
			String password = credentials.optString(GitConstants.KEY_PASSWORD, "");
			String url = credentials.optString(GitConstants.KEY_URL, db.getConfig().getString("remote", "origin", "url"));
			if(url!=null){
				Object cookie = request.getAttribute(GitConstants.KEY_SSO_TOKEN);
				String[] parsedUrl = parseSshGitUrl(url);
				String apiHost = getAPIHost(parsedUrl[0]);
				String user = parsedUrl[1];
				String project = parsedUrl[2];
				URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.BRANCH_LIST);
				ListPullRequestsJob job = new ListPullRequestsJob(TaskJobHandler.getUserId(request),url,cloneLocation, apiHost,user,project,username,password, cookie);
				return TaskJobHandler.handleTaskJob(request, response, job, statusHandler, JsonURIUnqualificationStrategy.ALL_NO_GIT);
			
			}
			OrionServlet.writeJSONResponse(request, response, new JSONArray(), JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;

		} catch (Exception ex) {
			String msg = "An error occured for pull request list command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}
	}
	
	public static String getAPIHost(String host) throws URISyntaxException {
		if(host.equals("github.com")){
			return "https://api.github.com/";
		}
		return "https://"+host+"/api/v3/";
	}
	
	public static String[] parseSshGitUrl(String url) throws URISyntaxException{
			String user = "", project = "";
			URIish uriish = new URIish(url);
			String[] scp = uriish.getPath().replaceFirst("^/", "").split("/", -1);;
			if(scp.length==2)
			{
				user = scp[0];
				project = uriish.getHumanishName();
			}
			return new String[]{uriish.getHost(),user,project};
	}
	
}
