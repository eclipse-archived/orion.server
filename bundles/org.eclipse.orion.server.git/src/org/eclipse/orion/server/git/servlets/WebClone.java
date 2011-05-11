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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.WebElement;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.resources.Base64Counter;
import org.eclipse.orion.server.git.GitConstants;
import org.osgi.service.prefs.BackingStoreException;

/**
 * A git clone created in Orion.
 */
public class WebClone extends WebElement {
	public static final String CLONE_NODE_NAME = "Clones"; //$NON-NLS-1$
	private static final Base64Counter cloneCounter = new Base64Counter();

	/**
	 * Creates a clone instance with the given globally unique id. The clone may
	 * or may not actually exist yet in the backing storage.
	 * 
	 * @param id
	 *            the globally unique clone id
	 * @return A clone instance with the given id
	 */
	public static WebClone fromId(String id) {
		WebClone clone = new WebClone((IEclipsePreferences) scope.getNode(CLONE_NODE_NAME).node(id));
		clone.setId(id);
		return clone;
	}

	/**
	 * Returns the next available clone id. The id is guaranteed to be globally
	 * unique within this server.
	 * 
	 * @return the next available project id, or <code>null</code> if an id
	 *         could not be allocated
	 */
	public static String nextCloneId() {
		synchronized (cloneCounter) {
			String candidate;
			do {
				candidate = cloneCounter.toString();
				cloneCounter.increment();
			} while (exists(candidate) || (!caseSensitive && containsUpperCase(candidate)));
			return candidate;
		}
	}

	/**
	 * Returns whether a clone with the given id already exists.
	 * 
	 * @param id
	 *            The id of the clone
	 * @return <code>true</code> if the clone already exists, and
	 *         <code>false</code> otherwise.
	 */
	public static boolean exists(String id) {
		try {
			return scope.getNode(CLONE_NODE_NAME).nodeExists(id);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Returns a list of all known web projects.
	 */
	public static List<WebClone> allClones() {
		List<WebClone> result = new ArrayList<WebClone>();
		IEclipsePreferences cloneRoot = scope.getNode(CLONE_NODE_NAME);
		try {
			String[] ids = cloneRoot.childrenNames();
			for (String id : ids)
				result.add(WebClone.fromId(id));
		} catch (BackingStoreException e) {
			LogHelper.log(e);
		}
		return result;
	}

	public WebClone(IEclipsePreferences store) {
		super(store);
	}

	public void remove() throws CoreException {
		try {
			store.removeNode();
		} catch (BackingStoreException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.PI_SERVER_CORE, "Error removing node"));
		}
	}

	/**
	 * Sets the location of the contents of this clone. The location is an
	 * absolute URI referencing to the filesystem.
	 */
	public void setContentLocation(URI contentURI) {
		store.put(ProtocolConstants.KEY_CONTENT_LOCATION, contentURI.toString());
	}

	/**
	 * Returns the location of the contents of this clone. The result is an
	 * absolute URI in the filesystem.
	 * <p>
	 * This method never returns null. Clone location will default to a relative
	 * URI containing only the project id.
	 * </p>
	 * 
	 * @return The location of the contents of this project
	 */
	public URI getContentLocation() {
		String result = store.get(ProtocolConstants.KEY_CONTENT_LOCATION, null);
		if (result != null) {
			try {
				return new URI(result);
			} catch (URISyntaxException e) {
				// fall through below
			}
		}
		// by default the location is simply the unique id of the project.
		return URI.create(getId());
	}

	/**
	 * Sets the git URL of this clone.
	 */
	public void setUrl(URIish gitURI) {
		store.put(GitConstants.KEY_URL, gitURI.toString());
	}

	/**
	 * Returns the URL of the git repository.
	 * <p>
	 * This method never returns null.
	 * </p>
	 * 
	 * @return The URL of the git repository
	 */
	public String getUrl() {
		String result = store.get(GitConstants.KEY_URL, null);
		if (result == null)
			throw new IllegalStateException();
		return result;
	}

}
