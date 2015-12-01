/*******************************************************************************
 * Copyright (c) 2011, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.jobs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.core.internal.preferences.Base64;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.tasks.TaskJob;
import org.eclipse.orion.server.git.GitActivator;
import org.eclipse.orion.server.git.GitCredentialsProvider;
import org.eclipse.orion.server.git.IGitHubTokenProvider;
import org.eclipse.orion.server.git.objects.PullRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A job to perform a clone operation in the background
 */
@SuppressWarnings("restriction")
public class ListPullRequestsJob  extends TaskJob {
	private HttpClient httpClient;
	
	private static final String CLIENT_KEY = "orion.oauth.github.client";

	private static final String CLIENT_SECRET = "orion.oauth.github.secret";

	private String url;

	private Repository db;

	private String remote;

	private URI cloneLocation;

	private String host;

	private String hostUser;

	private String project;

	private Cookie cookie;

	private String username;

	private String password;

	public ListPullRequestsJob(String userRunningTask, String url, URI cloneLocation, String host, String hostUser, String project, String username, String password, Object cookie) {
		super(userRunningTask, true);
		this.cloneLocation = cloneLocation;
		this.host = host;
		this.url = url;
		this.hostUser = hostUser;
		this.project = project;
		this.remote = userRunningTask;
		this.username = username;
		this.password = password;
		this.cookie = (Cookie) cookie;
		setFinalMessage("Getting Pull Requests Complete.");
		setTaskExpirationTime(TimeUnit.DAYS.toMillis(7));
	}
	
	private IStatus doList(IProgressMonitor monitor) throws JSONException, URISyntaxException, HttpException, IOException, CoreException {
		//JSONArray result = callGitHubAPI(url);
		JSONObject returnRes = new JSONObject();
		JSONArray resp= new JSONArray();

		String toCall =  host+"repos/"+hostUser+"/"+project+"/pulls";
		
		String token=null;
		Enumeration<IGitHubTokenProvider> providers = GitCredentialsProvider.GetGitHubTokenProviders();
		while (providers.hasMoreElements()) {
			token = providers.nextElement().getToken(this.url, remote);
			if (token != null) {
				break;
			}
		}
		GetMethod m;
		HttpClient hc = getHttpClient();
		if(token!=null){
			toCall += "?access_token="+token;
		}else{
			String client_secret = PreferenceHelper.getString(CLIENT_SECRET);
			String client_key = PreferenceHelper.getString(CLIENT_KEY);
			
			toCall += "?client_id="+client_key+"&client_secret="+client_secret+"";
		}

		m = new GetMethod(toCall);
		if(!this.username.isEmpty()&&!this.password.isEmpty()){
			String userCredentials = this.username+":"+this.password;
			String basicAuth = "Basic " + new String(Base64.encode(userCredentials.getBytes()));
		    m.setRequestHeader("Authorization", basicAuth);
		}

		try {
			hc.executeMethod(m);
			int statusCode =m.getStatusCode();
			if (statusCode == HttpStatus.SC_OK) {
				String res = IOUtilities.toString(m.getResponseBodyAsStream());
				resp= (res.isEmpty())?new JSONArray():new JSONArray(res);
 			}else if(statusCode == HttpServletResponse.SC_UNAUTHORIZED || statusCode == HttpServletResponse.SC_NOT_FOUND) {
 				String msg = "Repository not found, might be a private repository that requires authentication.";
				if (statusCode == HttpServletResponse.SC_UNAUTHORIZED) {
					msg = "Not authorized to get the repository information.";
				}

				IStatus result = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_UNAUTHORIZED, msg, new JSONObject(), null);
				ServerStatus status = ServerStatus.convert(result);
				JSONObject data = status.getJsonData();
				data.put("Url", this.url); //$NON-NLS-1$

				try {

					providers = GitCredentialsProvider.GetGitHubTokenProviders();
					while (providers.hasMoreElements()) {
						String authUrl = providers.nextElement().getAuthUrl(this.url, cookie);
						if (authUrl != null) {
							data.put("GitHubAuth", authUrl); //$NON-NLS-1$
							return result;
						}
					}
					return result;
				} catch (Exception ex) {
					/* fail silently, no GitHub auth url will be returned */
				}
				return result;
			}
		} finally {
			m.releaseConnection();
		}
		
		ArrayList<JSONObject> list = new ArrayList<JSONObject>();     
		if (resp != null) { 
		   int len = resp.length();
		   for (int i=0;i<len;i++){ 
		    list.add(new JSONObject(resp.get(i).toString()));
		   } 
		}
		JSONArray children = new JSONArray();
		for(JSONObject prJson:list){
			JSONObject base =prJson.getJSONObject("base");
			JSONObject head =prJson.getJSONObject("head");
			String htmlUrl =prJson.getString("html_url");
			PullRequest pr = new PullRequest(cloneLocation,db,base,head,htmlUrl);
			children.put(pr.toJSON());
		}
		returnRes.put(ProtocolConstants.KEY_CHILDREN, children);
		returnRes.put(ProtocolConstants.KEY_TYPE, PullRequest.TYPE);
		
		return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK, returnRes);
	}
		
	
	private HttpClient getHttpClient() {
		if (this.httpClient == null)
			this.httpClient = new HttpClient();
		return this.httpClient;
	}
	
	@Override
	protected IStatus performJob(IProgressMonitor monitor) {
		IStatus result = Status.OK_STATUS;
		try {
			result = doList(monitor);
		} catch (IOException e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error getting pull requests", e);
		} catch (CoreException e) {
			result = e.getStatus();
		} catch (Exception e) {
			result = new Status(IStatus.ERROR, GitActivator.PI_GIT, "Error getting pull requests", e);
		}
		return result;
	}
}
