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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.osgi.util.NLS;
import org.json.JSONArray;
import org.json.JSONObject;

public class GitSubmoduleHandlerV1 extends AbstractGitHandler {

	private final static int PAGE_SIZE = 50;

	GitSubmoduleHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected boolean handlePost(RequestInfo requestInfo) throws ServletException {

		JSONObject toAdd = requestInfo.getJSONRequest();
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		
		try {
			String targetPath = null;
			String gitUrl = toAdd.optString("GitUrl",null);
			String name = toAdd.optString("Name",null);
			if(gitUrl!=null){
				String workspacePath = ServletResourceHandler.toOrionLocation(request, toAdd.optString(ProtocolConstants.KEY_LOCATION, null));
				// expected path /file/{workspaceId}/{projectName}[/{path}]
				String filePathString = ServletResourceHandler.toOrionLocation(request, toAdd.optString(ProtocolConstants.KEY_PATH, null));
				IPath filePath = filePathString == null ? null : new Path(filePathString);
				if (filePath != null && filePath.segmentCount() < 3)
					filePath = null;
				if (filePath == null && workspacePath == null) {
					String msg = NLS.bind("Either {0} or {1} should be provided: {2}",
							new Object[] { ProtocolConstants.KEY_PATH, ProtocolConstants.KEY_LOCATION, toAdd });
					return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
				}
				ProjectInfo project = null;
				if (filePath != null) {
					// path format is /file/{workspaceId}/{projectName}/[filePath]
					project = GitUtils.projectFromPath(filePath);
					// workspace path format needs to be used if project does not exist
					if (project == null) {
						String msg = NLS.bind("Specified project does not exist: {0}", filePath.segment(2));
						return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null));
					}
					if (name == null)
						name = filePath.segmentCount() > 2 ? filePath.lastSegment() : project.getFullName();
				} else if (workspacePath != null) {

					// TODO: move this to CloneJob
					// if so, modify init part to create a new project if necessary
					if (name == null){
						IPath path = new Path(workspacePath);
						final IMetaStore metaStore = OrionConfiguration.getMetaStore();
						WorkspaceInfo workspace = metaStore.readWorkspace(path.segment(1));
						name = new URIish(gitUrl).getHumanishName();
						name = GitUtils.getUniqueProjectName(workspace, name);
					}

				}else{
					return false;
				}
				targetPath = targetPath == null? name:targetPath;
				GitUtils.addSubmodules(db, gitUrl,targetPath);
			}else{
				return false;
			}
			
			return true;
		} catch (Exception ex) {
			String msg = "An error occured for add submodule command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}
	}

	@Override
	protected boolean handlePut(RequestInfo requestInfo) throws ServletException {

		JSONObject requestPayload = requestInfo.getJSONRequest();
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;

		try {
			String operation = requestPayload.optString("Operation",null);
			if(operation==null){
				return false;
			}
			else if(operation.equals("update")){
				return GitUtils.updateSubmodules(db);
			}else if(operation.equals("sync")){
				return GitUtils.syncSubmodules(db);
			}else if(operation.equals("delete")){
				JSONArray parentsJSON = requestPayload.optJSONArray("Parents");
				String[] parents = parentsJSON.join(",").replace("\"","").split(",");
				String submodulePath = requestPayload.optString("SubmodulePath",null);
				if(submodulePath!=null && parents !=null && parents.length>0){
					GitUtils.removeSubmodule(submodulePath, parents);
					return true;
				}
			}
			return false;

		} catch (Exception ex) {
			String msg = "An error occured for update submodule command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}
	}

}
