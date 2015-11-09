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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.objects.PullRequest;
import org.eclipse.orion.server.servlets.JsonURIUnqualificationStrategy;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

public class GitPullRequestHandlerV1 extends AbstractGitHandler {

	private HttpClient httpClient;
	
	GitPullRequestHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handleGet(RequestInfo requestInfo) throws ServletException {

		JSONObject requestPayload = requestInfo.getJSONRequest();
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		try {
			
			String url = db.getConfig().getString("remote", "origin", "url");
			if(url!=null && isInGithub(url)){
				IPath p = new Path(url);
				
				String user = p.segment(1);
				String project = p.segment(2);
				project = project.endsWith(".git")?project.substring(0, project.length() - 4):project;
				JSONArray result = callGitHubAPI(user,project);
				ArrayList<JSONObject> list = new ArrayList<JSONObject>();     
				if (result != null) { 
				   int len = result.length();
				   for (int i=0;i<len;i++){ 
				    list.add(new JSONObject(result.get(i).toString()));
				   } 
				}
				URI cloneLocation = BaseToCloneConverter.getCloneLocation(getURI(request), BaseToCloneConverter.BRANCH_LIST);
				JSONObject returnRes = new JSONObject();
				JSONArray children = new JSONArray();
				for(JSONObject prJson:list){
					JSONObject base =prJson.getJSONObject("base");
					JSONObject head =prJson.getJSONObject("head");
					PullRequest pr = new PullRequest(cloneLocation,db,base,head);
					children.put(pr.toJSON());
				}
				returnRes.put(ProtocolConstants.KEY_CHILDREN, children);
				returnRes.put(ProtocolConstants.KEY_TYPE, PullRequest.TYPE);
				returnRes.put(ProtocolConstants.KEY_USER_NAME, user);

				OrionServlet.writeJSONResponse(request, response, returnRes, JsonURIUnqualificationStrategy.ALL_NO_GIT);
				return true;
			}
			OrionServlet.writeJSONResponse(request, response, new JSONArray(), JsonURIUnqualificationStrategy.ALL_NO_GIT);
			return true;

		} catch (Exception ex) {
			String msg = "An error occured for update submodule command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}
	}
	
	public static boolean isInGithub(String url) throws URISyntaxException {
	    URI uri = new URI(url);
	    String domain = uri.getHost();
	    domain = domain.startsWith("www.") ? domain.substring(4) : domain;
	    return domain.equals("github.com");
	}
	
	public JSONArray callGitHubAPI(String user, String project) throws GitAPIException, IOException, SAXException, JSONException {
		String toCall =  "https://api.github.com/repos/"+user+"/"+project+"/pulls?client_id=65c0190c137e8c4bb02a&client_secret=b8d338614992f60a42437c9aeb921f21fb1770cc";
		GetMethod m = new GetMethod(toCall);
		try {
			getHttpClient().executeMethod(m);
			if (m.getStatusCode() == HttpStatus.SC_OK) {
				return new JSONArray(IOUtilities.toString(m.getResponseBodyAsStream()));
			}
		} finally {
			m.releaseConnection();
		}
		return new JSONArray();
	}
	


	
	private HttpClient getHttpClient() {
		if (this.httpClient == null)
			this.httpClient = new HttpClient();
		return this.httpClient;
	}
	
}
