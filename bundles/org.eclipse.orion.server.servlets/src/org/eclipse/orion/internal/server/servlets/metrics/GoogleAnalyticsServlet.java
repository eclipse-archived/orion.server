/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.internal.server.servlets.metrics;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Servlet that returns the TID for logging Google Analytics data,
 * if there is one.
 */
public class GoogleAnalyticsServlet extends OrionServlet {

	private static final String KEY_TID = "orion.metrics.google.tid";
	private static final String KEY_SITESPEEDSAMPLERATE = "orion.metrics.google.sitespeed.sample";
	private static final long serialVersionUID = -76336740020069623L;

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		traceRequest(req);

		JSONObject result = new JSONObject();
		String tid = PreferenceHelper.getString(KEY_TID);
		String sampleRate = PreferenceHelper.getString(KEY_SITESPEEDSAMPLERATE);
		if (tid != null) {
			try {
				result.put("tid", tid);
				if (sampleRate != null) {
					try {
						int value = Integer.valueOf(sampleRate).intValue();
						if (0 <= value && value <= 100) {
							result.put("siteSpeedSampleRate", value);
						}
					} catch (NumberFormatException e) {
						/* ignore the value */
					}
				}
			} catch (JSONException e) {
				/* should not happen */
			}
		}
		writeJSONResponse(req, resp, result, null);
		resp.setHeader("Cache-Control", "public, max-age=86400, must-revalidate"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
