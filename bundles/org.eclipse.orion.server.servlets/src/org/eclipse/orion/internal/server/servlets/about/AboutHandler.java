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
package org.eclipse.orion.internal.server.servlets.about;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.core.runtime.IBundleGroup;
import org.eclipse.core.runtime.IBundleGroupProvider;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

/**
 * Handler for requests for information about the orion server. Some implementation details of this class were extracted from
 * {@link org.eclipse.help.internal.webapp.servlet.AboutServlet}
 * 
 * @author Anthony Hunter
 */
public class AboutHandler extends ServletResourceHandler<String> {

	private static final String XHTML_1 = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n<title>"; //$NON-NLS-1$
	private static final String XHTML_2 = "</title>\n <style type = \"text/css\"> td { padding-right : 10px; }</style></head>\n<body>\n"; //$NON-NLS-1$
	private static final String XHTML_3 = "</body>\n</html>"; //$NON-NLS-1$

	protected static final int NUMBER_OF_COLUMNS = 4;

	protected class PluginDetails {
		public String[] columns = new String[NUMBER_OF_COLUMNS];

		public PluginDetails(String[] columns) {
			this.columns = columns;
			for (int i = 0; i < NUMBER_OF_COLUMNS; i++) {
				if (columns[i] == null) {
					columns[i] = ""; //$NON-NLS-1$
				}
			}
		}
	}

	protected class PluginComparator implements Comparator<PluginDetails> {

		public PluginComparator(int column) {
			this.column = column;
		}

		private int column;

		@Override
		public int compare(PluginDetails pd1, PluginDetails pd2) {
			return Collator.getInstance().compare(pd1.columns[column], pd2.columns[column]);
		}

	}

	protected ServletResourceHandler<IStatus> statusHandler;

	public AboutHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	private boolean handleGetRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		response.setContentType("text/html; charset=UTF-8"); //$NON-NLS-1$
		StringBuffer buf = new StringBuffer();
		buf.append(XHTML_1);
		String sortParam = request.getParameter("sortColumn"); //$NON-NLS-1$
		int sortColumn = 3;
		if (sortParam != null) {
			try {
				sortColumn = Integer.parseInt(sortParam);
			} catch (NumberFormatException e) {
			}
		}

		buf.append("About");
		buf.append(XHTML_2);
		String app = System.getProperty("eclipse.application", "org.eclipse.orion.application"); //$NON-NLS-1$
		String build = getBuildId();
		if (app != null || build != null) {
			buf.append("<table><tr><td><img src=\"../webapp/orion-96.png\"/></td><td>"); //$NON-NLS-1$
			buf.append("<p>"); //$NON-NLS-1$
			if (app != null)
				buf.append("Application: " + app + "<br/>");//$NON-NLS-1$ //$NON-NLS-2$
			if (build != null)
				buf.append("Build Id: " + build + "<br/>");//$NON-NLS-1$ //$NON-NLS-2$
			buf.append("</p></td></tr></table>"); //$NON-NLS-1$
		}

		buf.append("<table>"); //$NON-NLS-1$
		List<PluginDetails> plugins = new ArrayList<PluginDetails>();

		Bundle[] bundles = Activator.getDefault().getContext().getBundles();
		for (int k = 0; k < bundles.length; k++) {
			plugins.add(pluginDetails(bundles[k]));
		}

		Comparator<PluginDetails> pluginComparator = new PluginComparator(sortColumn);
		Collections.sort(plugins, pluginComparator);
		String[] headerColumns = new String[] { "Provider", //$NON-NLS-1$
				"PluginName", //$NON-NLS-1$
				"Version", //$NON-NLS-1$
				"Identifier" //$NON-NLS-1$
		};
		PluginDetails header = new PluginDetails(headerColumns);
		buf.append(headerRowFor(header));
		for (Iterator<PluginDetails> iter = plugins.iterator(); iter.hasNext();) {
			PluginDetails details = iter.next();
			buf.append(tableRowFor(details));
		}
		buf.append("</table>"); //$NON-NLS-1$
		buf.append(XHTML_3);
		String output = buf.toString();
		try {
			response.getWriter().write(output);
		} catch (IOException e) {
			// should not occur
			throw new RuntimeException(e);
		}
		return true;
	}

	private String headerRowFor(PluginDetails details) {
		String row = "<tr>\n"; //$NON-NLS-1$
		for (int i = 0; i < NUMBER_OF_COLUMNS; i++) {
			row += ("<td><a href = \"about.html?sortColumn="); //$NON-NLS-1$
			row += i;
			row += "\">"; //$NON-NLS-1$
			row += details.columns[i];
			row += "</a></td>\n"; //$NON-NLS-1$
		}
		row += "</tr>"; //$NON-NLS-1$
		return row;
	}

	private String tableRowFor(PluginDetails details) {
		String row = "<tr>\n"; //$NON-NLS-1$
		for (int i = 0; i < NUMBER_OF_COLUMNS; i++) {
			row += ("<td>"); //$NON-NLS-1$
			row += details.columns[i];
			row += "</td>\n"; //$NON-NLS-1$
		}
		row += "</tr>"; //$NON-NLS-1$
		return row;
	}

	protected PluginDetails pluginDetails(Bundle bundle) {
		String[] values = new String[] { getResourceString(bundle, Constants.BUNDLE_VENDOR), getResourceString(bundle, Constants.BUNDLE_NAME),
				getResourceString(bundle, Constants.BUNDLE_VERSION), bundle.getSymbolicName() };
		PluginDetails details = new PluginDetails(values);

		return details;
	}

	private static String getResourceString(Bundle bundle, String headerName) {
		String value = bundle.getHeaders().get(headerName);
		return value == null ? null : Platform.getResourceString(bundle, value);
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException {
		switch (getMethod(request)) {
		case GET:
			return handleGetRequest(request, response, path);
		default:
			return false;
		}
	}

	/**
	 * Get the build id for the orion application by using the bundle group version from the org.eclipse.orion feature. The maven build assigns this feature the
	 * same timestamp as the build timestamp.
	 * 
	 * @return the build id.
	 */
	private String getBuildId() {
		String version = System.getProperty("eclipse.buildId", "unknown"); //$NON-NLS-1$
		String featureId = "org.eclipse.orion";
		IBundleGroupProvider[] providers = Platform.getBundleGroupProviders();
		if (providers != null) {
			for (IBundleGroupProvider provider : providers) {
				IBundleGroup[] bundleGroups = provider.getBundleGroups();
				for (IBundleGroup group : bundleGroups) {
					if (group.getIdentifier().equals(featureId)) {
						version = group.getVersion();
					}
				}
			}
		}
		return version;
	}
}
