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

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_SUBMODULE_SECTION;
import static org.eclipse.jgit.lib.Constants.DOT_GIT_MODULES;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.SubmoduleAddCommand;
import org.eclipse.jgit.api.SubmoduleInitCommand;
import org.eclipse.jgit.api.SubmoduleStatusCommand;
import org.eclipse.jgit.api.SubmoduleSyncCommand;
import org.eclipse.jgit.api.SubmoduleUpdateCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitUtils.Traverse;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;

public class GitSubmoduleHandlerV1 extends AbstractGitHandler {

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
			String gitUrl = toAdd.optString(GitConstants.KEY_URL,null);
			String name = toAdd.optString(ProtocolConstants.KEY_NAME,null);
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
				addSubmodules(db, gitUrl,targetPath);
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
				return updateSubmodules(db);
			}else if(operation.equals("sync")){
				return syncSubmodules(db);
			}
			return false;

		} catch (Exception ex) {
			String msg = "An error occured for update submodule command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}
	}
	
	@Override
	protected boolean handleDelete(RequestInfo requestInfo) throws ServletException {
		HttpServletRequest request = requestInfo.request;
		HttpServletResponse response = requestInfo.response;
		Repository db = requestInfo.db;
		Repository parentRepo = null;
		try {
			Map<IPath, File> parents = GitUtils.getGitDirs(requestInfo.filePath.removeLastSegments(1), Traverse.GO_UP);
			if (parents.size() < 1)
				return false;
			parentRepo = FileRepositoryBuilder.create(parents.entrySet().iterator().next().getValue());
			String pathToSubmodule = db.getWorkTree().toString().substring(parentRepo.getWorkTree().toString().length() + 1);
			removeSubmodule(parentRepo, pathToSubmodule);
			return true;
		} catch (Exception ex) {
			String msg = "An error occured for delete submodule command.";
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex));
		}finally{
			if (parentRepo != null) {
				parentRepo.close();
			}
		}
    
	}

	public static boolean updateSubmodules(Repository repo) throws GitAPIException {
		SubmoduleInitCommand init = new SubmoduleInitCommand(repo);
		init.call();
		SubmoduleUpdateCommand update = new SubmoduleUpdateCommand(repo);
		Collection<String> updated = update.call();
		SubmoduleStatusCommand status = new SubmoduleStatusCommand(repo);
		Map<String, SubmoduleStatus> statusResult = status.call();
		return updated.size() == statusResult.size();
	}

	public static boolean syncSubmodules(Repository repo) throws GitAPIException {
		SubmoduleSyncCommand sync = new SubmoduleSyncCommand(repo);
		Map<String, String> synced = sync.call();
		SubmoduleStatusCommand status = new SubmoduleStatusCommand(repo);
		Map<String, SubmoduleStatus> statusResult = status.call();
		return synced.size() == statusResult.size();
	}

	public static void addSubmodules(Repository repo, String targetUrl, String targetPath) throws GitAPIException {
		SubmoduleAddCommand addCommand = new SubmoduleAddCommand(repo);
		addCommand.setURI(targetUrl);
		addCommand.setPath(targetPath);
		Repository repository = addCommand.call();
		repository.close();
	}
	
	public static void removeSubmodule(Repository parentRepo, String pathToSubmodule) throws Exception {
		pathToSubmodule = pathToSubmodule.replace("\\", "/");
		StoredConfig gitSubmodulesConfig = getGitSubmodulesConfig(parentRepo);
		gitSubmodulesConfig.unsetSection(CONFIG_SUBMODULE_SECTION, pathToSubmodule);
		gitSubmodulesConfig.save();
		StoredConfig repositoryConfig = parentRepo.getConfig();
		repositoryConfig.unsetSection(CONFIG_SUBMODULE_SECTION, pathToSubmodule);
		repositoryConfig.save();
		Git git = Git.wrap(parentRepo);
		git.add().addFilepattern(DOT_GIT_MODULES).call();
		RmCommand rm = git.rm().addFilepattern(pathToSubmodule);
		if (gitSubmodulesConfig.getSections().size() == 0) {
			rm.addFilepattern(DOT_GIT_MODULES);
		}
		rm.call();
		FileUtils.delete(new File(parentRepo.getWorkTree(), pathToSubmodule), FileUtils.RECURSIVE);
		FileUtils.delete(new File(parentRepo.getWorkTree(), Constants.DOT_GIT +"/"+Constants.MODULES+"/"+pathToSubmodule), FileUtils.RECURSIVE);
	}
	
	private static StoredConfig getGitSubmodulesConfig( Repository repository ) throws IOException, ConfigInvalidException {
		File gitSubmodulesFile = new File( repository.getWorkTree(), DOT_GIT_MODULES );
		FileBasedConfig gitSubmodulesConfig = new FileBasedConfig( null, gitSubmodulesFile, FS.DETECTED );
		gitSubmodulesConfig.load();
		return gitSubmodulesConfig;
	}
}
