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
package org.eclipse.orion.internal.server.servlets.project;

import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.eclipse.orion.server.core.users.EclipseWebScope;

import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;


import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;

/**
 * An Eclipse web project.
 */
public class WebProject extends WebElement {
	public static final String PROJECT_NODE_NAME = "Projects"; //$NON-NLS-1$
	private static final Base64Counter projectCounter = new Base64Counter();

	/**
	 * Creates a project instance with the given globally unique id. The project
	 * may or may not actually exist yet in the backing storage.
	 * @param id the globally unique workspace id
	 * @return A workspace instance with the given id
	 */
	public static WebProject fromId(String id) {
		IEclipsePreferences projects = new EclipseWebScope().getNode(PROJECT_NODE_NAME);
		WebProject result = new WebProject((IEclipsePreferences) projects.node(id));
		result.setId(id);
		return result;
	}

	/**
	 * Returns whether a project with the given id already exists.
	 * @param id The id of the project
	 * @return <code>true</code> if the project already exists, and <code>false</code> otherwise.
	 */
	public static boolean exists(String id) {
		try {
			return new EclipseWebScope().getNode(PROJECT_NODE_NAME).nodeExists(id);
		} catch (BackingStoreException e) {
			return false;
		}
	}

	/**
	 * Returns the next available project id. The id is guaranteed to be globally unique within
	 * this server.
	 * @return the next available project id, or <code>null</code> if an id could not be allocated
	 */
	public static String nextProjectId() {
		try {
			File root = EFS.getStore(Activator.getDefault().getRootLocationURI()).toLocalFile(EFS.NONE, null);
			synchronized (projectCounter) {
				File candidate;
				while (!(candidate = new File(root, projectCounter.toString())).mkdir()) {
					projectCounter.increment();
				}
				//move the counter to the next possibly available project
				projectCounter.increment();
				return candidate.getName();
			}
		} catch (CoreException e) {
			return null;
		}
	}

	/**
	 * Returns a list of all known web projects.
	 */
	public static List<WebProject> allProjects() {
		IEclipsePreferences projects = new EclipseWebScope().getNode(WebProject.PROJECT_NODE_NAME);
		List<WebProject> result = new ArrayList<WebProject>();
		try {
			String[] ids = projects.childrenNames();
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
	 * relative to the file servlet location, or an absolute URI in the 
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
