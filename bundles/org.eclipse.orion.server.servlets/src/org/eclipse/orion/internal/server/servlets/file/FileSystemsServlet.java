/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.file;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.*;

/**
 * Handles information about supported file systems.<br>
 * <b>GET /filesystems</b> returns supported file systems in JSON, currently known:
 * <ul><li><b>file</b> - local disc file system</li>
 * <li><b>gitfs</b> - git repository</li></ul>
 */
public class FileSystemsServlet extends OrionServlet {

	private static final long serialVersionUID = -9128107386674956051L;

	private boolean isGitAccesable() {
		try {
			// check that Git FS exists
			EFS.getFileSystem("gitfs"); //$NON-NLS-1$
			return true;
		} catch (Exception e) {
			LogHelper.log(new Status(IStatus.INFO, Activator.PI_SERVER_SERVLETS, 1, "Git file system is not accessible", e)); //$NON-NLS-1$
			return false;
		}
	}

	private JSONObject formJSON(String fsName, boolean advanced) throws JSONException {
		JSONObject object = new JSONObject();
		object.put("name", fsName); //$NON-NLS-1$
		object.put("advanced", advanced); //$NON-NLS-1$
		return object;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			JSONObject json = new JSONObject();
			JSONArray array = new JSONArray();
			array.put(formJSON("file", false)); //$NON-NLS-1$
			if (isGitAccesable()) {
				array.put(formJSON("gitfs", true)); //$NON-NLS-1$
			}
			json.put("items", array); //$NON-NLS-1$
			writeJSONResponse(req, resp, json);
			return;
		} catch (JSONException e) {
			handleException(resp, "Cannot get items", e);
		}
	}
}
