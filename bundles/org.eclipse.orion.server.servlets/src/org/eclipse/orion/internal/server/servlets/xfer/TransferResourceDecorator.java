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
package org.eclipse.orion.internal.server.servlets.xfer;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.servlets.file.NewFileServlet;
import org.eclipse.orion.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Adds links to the import and export services for files in the workspace.
 */
public class TransferResourceDecorator implements IWebResourceDecorator {

	/*(non-Javadoc)
	 * @see org.eclipse.orion.internal.server.core.IWebResourceDecorator#addAtributesFor(java.net.URI, org.json.JSONObject)
	 */
	public void addAtributesFor(HttpServletRequest request, URI resource, JSONObject representation) {

		String servlet = request.getServletPath();
		if (!"/file".equals(servlet) && !"/workspace".equals(servlet))
			return;
		try {
			//don't add import/export directly on a workspace at this point
			if ("/file".equals(servlet))
				addTransferLinks(request, resource, representation);
			JSONArray children = representation.optJSONArray(ProtocolConstants.KEY_CHILDREN);
			if (children != null) {
				for (int i = 0; i < children.length(); i++) {
					JSONObject child = children.getJSONObject(i);
					if (child.optBoolean(ProtocolConstants.KEY_DIRECTORY)) {
						addTransferLinks(request, resource, child);
					}
				}
			}
		} catch (Exception e) {
			//log and continue
			LogHelper.log(e);
		}
	}

	private void addTransferLinks(HttpServletRequest request, URI resource, JSONObject representation) throws URISyntaxException, JSONException, UnsupportedEncodingException {
		if (!representation.has(ProtocolConstants.KEY_LOCATION)) return;
		URI location = new URI(representation.getString(ProtocolConstants.KEY_LOCATION));
		IPath targetPath = new Path(location.getPath()).removeFirstSegments(1).removeTrailingSeparator();
		IPath path = new Path("/xfer/import").append(targetPath); //$NON-NLS-1$
		URI link = new URI(resource.getScheme(), resource.getAuthority(), path.toString(), null, null);
		if (representation.has(ProtocolConstants.KEY_EXCLUDED_IN_IMPORT)) {
			String linkString = link.toString();
			if (linkString.contains("?")) {
				linkString += "&" + ProtocolConstants.PARAM_EXCLUDE + "=" + URLEncoder.encode(representation.getString(ProtocolConstants.KEY_EXCLUDED_IN_IMPORT), "UTF-8");
			} else {
				linkString += "?" + ProtocolConstants.PARAM_EXCLUDE + "=" + URLEncoder.encode(representation.getString(ProtocolConstants.KEY_EXCLUDED_IN_IMPORT), "UTF-8");
			}
			link = new URI(linkString);
			representation.remove(ProtocolConstants.KEY_EXCLUDED_IN_IMPORT);
		}
		representation.put(ProtocolConstants.KEY_IMPORT_LOCATION, link);
		//Bug 348073: don't add export links for empty directories
		if (isEmptyDirectory(request, targetPath)) {
			return;
		}
		path = new Path("/xfer/export").append(targetPath).addFileExtension("zip"); //$NON-NLS-1$ //$NON-NLS-2$
		link = new URI(resource.getScheme(), resource.getAuthority(), path.toString(), null, null);
		if (representation.has(ProtocolConstants.KEY_EXCLUDED_IN_EXPORT)) {
			String linkString = link.toString();
			if (linkString.contains("?")) {
				linkString += "&" + ProtocolConstants.PARAM_EXCLUDE + "=" + URLEncoder.encode(representation.getString(ProtocolConstants.KEY_EXCLUDED_IN_EXPORT), "UTF-8");
			} else {
				linkString += "?" + ProtocolConstants.PARAM_EXCLUDE + "=" + URLEncoder.encode(representation.getString(ProtocolConstants.KEY_EXCLUDED_IN_EXPORT), "UTF-8");
			}
			link = new URI(linkString);
			representation.remove(ProtocolConstants.KEY_EXCLUDED_IN_EXPORT);
		}
		representation.put(ProtocolConstants.KEY_EXPORT_LOCATION, link);
	}

	private boolean isEmptyDirectory(HttpServletRequest request, IPath targetPath) {
		IFileStore store = NewFileServlet.getFileStore(request, targetPath);
		//if an error occurred we can't tell, so assume non-empty to be safe
		if (store == null) {
			return false;
		}
		try {
			return store.childNames(EFS.NONE, null).length == 0;
		} catch (CoreException e) {
			//this isn't the place for reporting this failure, so assume non-empty
			return false;
		}
	}
}