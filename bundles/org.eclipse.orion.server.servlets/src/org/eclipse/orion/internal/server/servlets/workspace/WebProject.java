/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.osgi.service.prefs.BackingStoreException;

/**
 * An Eclipse web project.
 */
public class WebProject extends WebElement {
	public static final String PROJECT_NODE_NAME = "Projects"; //$NON-NLS-1$
	private static final Base64Counter projectCounter = new Base64Counter();

	/**
	 * Creates a workspace instance with the given globally unique id. The workspace
	 * may or may not actually exist yet in the backing storage.
	 * @param id the globally unique workspace id
	 * @return A workspace instance with the given id
	 */
	public static WebProject fromId(String id) {
		WebProject result = new WebProject((IEclipsePreferences) scope.getNode(PROJECT_NODE_NAME).node(id));
		result.setId(id);
		return result;
	}

	/**
	 * Returns the next available project id. The id is guaranteed to be globally unique within
	 * this server.
	 * @return the next available project id, or <code>null</code> if an id could not be allocated
	 */
	public static String nextProjectId() {
		synchronized (projectCounter) {
			String candidate;
			do {
				candidate = projectCounter.toString();
				projectCounter.increment();
			} while (exists(candidate));
			return candidate;
		}
	}

	/**
	 * Returns whether a project  with the given id already exists.
	 * @param id The id of the project
	 * @return <code>true</code> if the project already exists, and <code>false</code> otherwise.
	 */
	public static boolean exists(String id) {
		try {
			return scope.getNode(PROJECT_NODE_NAME).nodeExists(id);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Returns a list of all known web projects.
	 */
	public static List<WebProject> allProjects() {
		List<WebProject> result = new ArrayList<WebProject>();
		IEclipsePreferences projectRoot = scope.getNode(PROJECT_NODE_NAME);
		try {
			String[] ids = projectRoot.childrenNames();
			for (String id : ids)
				result.add(WebProject.fromId(id));
		} catch (BackingStoreException e) {
			LogHelper.log(e);
		}
		return result;
	}

	public WebProject(IEclipsePreferences store) {
		super(store);
	}

	public void remove() {
		store.remove(ProtocolConstants.KEY_CONTENT_LOCATION);
		store.remove(ProtocolConstants.KEY_ID);
		store.remove(ProtocolConstants.KEY_NAME);
	}

	/**
	 * Sets the location of the contents of this project. The location is either
	 * relative to the workspace servlet location, or an absolute URI in the 
	 * case where content is stored on a different server.
	 */
	public void setContentLocation(URI contentURI) {
		store.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentURI.toString());
	}

	/**
	 * Returns the location of the contents of this project. The result is either
	 * relative to the workspace servlet location, or an absolute URI in the 
	 * case where content is stored on a different server.
	 * <p>
	 * This method never returns null. Project location will default to a relative
	 * URI containing only the project id.
	 * </p>
	 * @return The location of the contents of this project
	 */
	public URI getContentLocation() {
		String result = store.get(ProtocolConstants.KEY_CONTENT_LOCATION, null);
		if (result != null) {
			try {
				return new URI(result);
			} catch (URISyntaxException e) {
				//fall through below
			}
		}
		//by default the location is simply the unique id of the project.
		return URI.create(getId());
	}

}
