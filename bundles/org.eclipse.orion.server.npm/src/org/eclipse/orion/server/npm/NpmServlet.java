/*******************************************************************************
 * Copyright (c) 2010, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.npm;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
//import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.eclipse.orion.internal.server.servlets.file.*;
import org.eclipse.core.filesystem.*;
import org.eclipse.orion.server.core.PreferenceHelper;

//import org.eclipse.core.runtime.IStatus;
//import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
//import org.eclipse.orion.server.core.ServerStatus;

import java.io.*;

import org.json.JSONException;
import org.json.JSONObject;

/*
 * 
 */
public class NpmServlet extends OrionServlet {

	private static final long serialVersionUID = 1L;

	public NpmServlet() {
		super();
	}

	@Override
	public void init() throws ServletException {
		super.init();
	}

	@Override
	public void destroy() {
		super.destroy();
	}

	private String excuteCmd(String type, String cwd, String args) {
		String result = "";
		try {
			IPath path = new Path(cwd).removeFirstSegments(1);
			IFileStore fStore = NewFileServlet.getFileStore(null, path);
			if (fStore == null) {
				result = "Error: Can not use this command in the workspace root\n";
				return result;
			}
			String cwdPath = fStore.toString();
			String newArgs;
			if (args != null) {
				newArgs = " " + args;
			} else {
				newArgs = "";
			}
			String npmPath = PreferenceHelper.getString("orion.npmPath");
			if(npmPath == null || npmPath.isEmpty()){
				result = "Error: Npm path is not defined, contact the server administrator.\n";
				return result;
			}
			String cmd = npmPath + " " + type + newArgs;
			Process p = Runtime.getRuntime().exec(cmd, null, new File(cwdPath));
			
			BufferedReader bri = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			BufferedReader bre = new BufferedReader(new InputStreamReader(
					p.getErrorStream()));
			String line;
			while ((line = bri.readLine()) != null) {
				result = result + line + "\n";
			}
			bri.close();
			while ((line = bre.readLine()) != null) {
				result = result + line + "\n";
			}
			bre.close();
			p.waitFor();
			result = result + "Done.\n";
			// System.out.println("Done.");
		} catch (Exception err) {
			err.printStackTrace();
		}
		return result;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			JSONObject o = OrionServlet.readJSONRequest(req);
			String type = o.getString("type");
			String cwd = o.getString("cwd");
			String args = null;
			if (type.equalsIgnoreCase("install")) {
				args = o.getString("package");
			}
			String result = this.excuteCmd(type, cwd, args);

			resp.setStatus(HttpServletResponse.SC_OK);
			JSONObject jsonResult = new JSONObject();
			jsonResult.put("cmdOutput", result);
			OrionServlet.writeJSONResponse(req, resp, jsonResult);
		} catch (JSONException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					e.getMessage());
		} catch (IOException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					e.getMessage());
		}
	}
}
