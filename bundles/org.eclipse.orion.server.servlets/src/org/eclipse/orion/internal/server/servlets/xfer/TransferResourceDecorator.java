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
package org.eclipse.orion.internal.server.servlets.xfer;

import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.internal.server.core.IWebResourceDecorator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.json.*;

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
					if (child.getBoolean(ProtocolConstants.KEY_DIRECTORY)) {
						addTransferLinks(request, resource, child);
					}
				}
			}
		} catch (Exception e) {
			//log and continue
			LogHelper.log(e);
		}
	}

	private void addTransferLinks(HttpServletRequest request, URI resource, JSONObject representation) throws URISyntaxException, JSONException {
		URI location = new URI(representation.getString(ProtocolConstants.KEY_LOCATION));
		IPath targetPath = new Path(location.getPath()).removeFirstSegments(1).removeTrailingSeparator();
		IPath path = new Path("/xfer/import").append(targetPath); //$NON-NLS-1$
		URI link = new URI(resource.getScheme(), resource.getAuthority(), path.toString(), null, null);
		representation.put(ProtocolConstants.KEY_IMPORT_LOCATION, link);
		path = new Path("/xfer/export").append(targetPath).addFileExtension("zip"); //$NON-NLS-1$ //$NON-NLS-2$
		link = new URI(resource.getScheme(), resource.getAuthority(), path.toString(), null, null);
		representation.put(ProtocolConstants.KEY_EXPORT_LOCATION, link);
	}
}