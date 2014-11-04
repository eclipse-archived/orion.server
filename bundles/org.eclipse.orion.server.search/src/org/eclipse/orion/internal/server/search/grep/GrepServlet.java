/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search.grep;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Aidan Redpath
 */
public class GrepServlet extends OrionServlet {

	private static final long serialVersionUID = 1L;

	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			FileGrepper grepper = new FileGrepper(req, resp);
			List<File> files = grepper.search();
			writeResponse(req, resp, files);
		} catch (GrepException e) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	private void writeResponse(HttpServletRequest req, HttpServletResponse resp, List<File> files) {
		try {
			JSONObject json = convertListToJson(files);
			PrintWriter writer = resp.getWriter();
			writer.write(json.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private JSONObject convertListToJson(List<File> files) {
		JSONObject json = new JSONObject();
		LinkedList<String> filenames = new LinkedList<String>();
		for (File file : files) {
			filenames.add(file.getPath());
		}
		try {
			json.put("files", filenames);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return json;
	}
}
