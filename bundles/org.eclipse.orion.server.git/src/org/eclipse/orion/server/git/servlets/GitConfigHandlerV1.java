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
import java.net.URISyntaxException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.orion.internal.server.servlets.ServerStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;

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
		Repository db = null;
		try {
			Path p = new Path(path);
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, db, p);
					// case PUT:
					// return handlePut(request, response, p);
					//				case POST :
					//					return handlePost(request, response, db, p);
					// case DELETE :
					// return handleDelete(request, response, p);
			}
		} catch (Exception e) {
			String msg = NLS.bind("Failed to process an operation on commits for {0}", path); //$NON-NLS-1$
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e));
		} finally {
			if (db != null)
				db.close();
		}
		return false;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, Repository db, Path path) throws IOException, JSONException, ServletException, URISyntaxException, CoreException {
		File gitDir = GitUtils.getGitDir(path.removeFirstSegments(1).uptoSegment(2), request.getRemoteUser());
		if (gitDir == null)
			return false; // TODO: or an error response code, 405?
		db = new FileRepository(gitDir);

		db.getConfig().get(new SectionParser<Object>() {
			@Override
			public Object parse(Config cfg) {
				System.out.println(cfg);
				return null;
			}
		});

		//		List<WebClone> clones = WebClone.allClones();
		//		JSONObject result = new JSONObject();
		//		URI baseLocation = getURI(request);
		//		JSONArray children = new JSONArray();
		//		for (WebClone clone : clones) {
		//			JSONObject child = WebCloneResourceHandler.toJSON(clone, baseLocation);
		//			children.put(child);
		//		}
		//		result.put(ProtocolConstants.KEY_CHILDREN, children);
		//		OrionServlet.writeJSONResponse(request, response, result);
		return true;
	}

	//	private void doConfigureClone(Git git, String userId) throws IOException, CoreException {
	//		StoredConfig config = git.getRepository().getConfig();
	//		IOrionUserProfileNode userNode = UserServiceHelper.getDefault().getUserProfileService().getUserProfileNode(userId, true).getUserProfileNode(IOrionUserProfileConstants.GENERAL_PROFILE_PART);
	//		if (userNode.get(GitConstants.KEY_NAME, null) != null)
	//			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, userNode.get(GitConstants.KEY_NAME, null));
	//		if (userNode.get(GitConstants.KEY_MAIL, null) != null)
	//			config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, userNode.get(GitConstants.KEY_MAIL, null));
	//		config.save();
	//	}

	/**
	 * Validates that the provided clone name is valid. Returns
	 * <code>true</code> if the project name is valid, and <code>false</code>
	 * otherwise. This method takes care of setting the error response when the
	 * clone name is not valid.
	 */
	//	private boolean validateCloneName(String name, HttpServletRequest request, HttpServletResponse response) throws ServletException {
	//		// TODO: implement
	//		return true;
	//	}

	//	private boolean validateCloneUrl(String url, HttpServletRequest request, HttpServletResponse response) throws ServletException {
	//		if (url == null || url.trim().length() == 0) {
	//			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Clone URL cannot be empty", null)); //$NON-NLS-1$
	//			return false;
	//		}
	//		try {
	//			new URIish(url);
	//		} catch (URISyntaxException e) {
	//			statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, NLS.bind("Invalid clone URL: {0}", url), e)); //$NON-NLS-1$
	//			return false;
	//		}
	//		return true;
	//	}

}
