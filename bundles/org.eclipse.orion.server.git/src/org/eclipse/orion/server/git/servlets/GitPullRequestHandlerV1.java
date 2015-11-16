/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
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

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.internal.server.servlets.task.TaskJobHandler;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.jobs.ListPullRequestsJob;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;

public class GitPullRequestHandlerV1 extends AbstractGitHandler {

	GitPullRequestHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		try {
			
			String url = db.getConfig().getString("remote", "origin", "url");
			if(url!=null){
				Object cookie = request.getAttribute(GitConstants.KEY_SSO_TOKEN);
				String[] parsedUrl = parseSshGitUrl(url);
				String apiHost = getAPIHost(parsedUrl[0]);
				String user = parsedUrl[1];
				String project = parsedUrl[2];
				URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.BRANCH_LIST);
				ListPullRequestsJob job = new ListPullRequestsJob(TaskJobHandler.getUserId(request),url,cloneLocation, apiHost,user,project, cookie);
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
	
	public static String[] parseSshGitUrl(String url){
		try {
			URI uri = new URI(url);
			IPath p = new Path(url);
			String user = "", project = "";
			if(p.segments().length==3){
				user = p.segment(1);
				project = p.segment(2);
				project = project.endsWith(".git") ? project.substring(0, project.length() - 4) : project;
			}
			return new String[]{uri.getHost(),user,project};
					
		} catch(Exception e){
			try {
				String[] scp = url.split(":"); //$NON-NLS-0$
				String user = "", project = "";
				if(scp.length==2){
					String[] unp = scp[1].split("/");
					if(unp.length==2)
					user = unp[0];
					project = unp[1];
					project = project.endsWith(".git") ? project.substring(0, project.length() - 4) : project;
				}
				String[] hostPart = scp[0].split("@"); //$NON-NLS-0$
				String host = hostPart.length > 1 ? hostPart[1] : hostPart[0];
				return new String[]{host,user,project};
				
			} catch(Exception ex){
				return new String[]{"","",""};
			}
		}
	}
	
}
