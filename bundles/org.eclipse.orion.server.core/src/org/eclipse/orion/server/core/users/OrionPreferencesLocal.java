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
package org.eclipse.orion.server.core.users;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.orion.internal.server.core.Activator;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.*;

/**
 * A local file system implementation of storage for the eclipse web preference
 * scope.
 */
public class OrionPreferencesLocal extends EclipsePreferences {

	// cached values
	private int segmentCount;
	private String qualifier;
	private IPath location;
	private IEclipsePreferences loadLevel;
	// cache which nodes have been loaded from disk
	private static Set<String> loadedNodes = Collections.synchronizedSet(new HashSet<String>());
	private static boolean initialized = false;
	private static IPath baseLocation;

	static {
		baseLocation = computeLocation();
	}

	/**
	 * Default constructor. Should only be called by #createExecutableExtension.
	 */
	public OrionPreferencesLocal() {
		this(null, null);
	}

	private static IPath computeLocation() {
		BundleContext context = Activator.getDefault().getContext();
		Collection<ServiceReference<Location>> refs;
		try {
			refs = context.getServiceReferences(Location.class, Location.INSTANCE_FILTER);
		} catch (InvalidSyntaxException e) {
			//we know the instance location filter syntax is valid
			throw new RuntimeException(e);
		}
		if (refs.isEmpty())
			return null;
		ServiceReference<Location> ref = refs.iterator().next();
		Location location = context.getService(ref);
		try {
			if (location == null)
				return null;
			URL root;
			try {
				root = location.getDataArea(Activator.PI_SERVER_CORE);
			} catch (IOException e) {
				return null;
			}
			if (root == null)
				return null;
			//strip off file: prefix from URL
			return new Path(root.toExternalForm().substring(5));
		} finally {
			context.ungetService(ref);
		}
	}

	private OrionPreferencesLocal(EclipsePreferences parent, String name) {
		super(parent, name);

		initializeChildren();

		// cache the segment count
		String path = absolutePath();
		segmentCount = getSegmentCount(path);
		if (segmentCount < 2)
			return;

		// cache the qualifier
		qualifier = getSegment(path, 1);

		// cache the location
		if (qualifier == null)
			return;
		if (baseLocation != null)
			location = computeLocation(baseLocation, qualifier);
	}

	protected IPath getLocation() {
		return location;
	}

	protected boolean isAlreadyLoaded(IEclipsePreferences node) {
		return loadedNodes.contains(node.name());
	}

	protected void loaded() {
		loadedNodes.add(name());
	}

	/*
	 * Return the node at which these preferences are loaded/saved.
	 */
	protected IEclipsePreferences getLoadLevel() {
		if (loadLevel == null) {
			if (qualifier == null)
				return null;
			// Make it relative to this node rather than navigating to it from the root.
			// Walk backwards up the tree starting at this node.
			// This is important to avoid a chicken/egg thing on startup.
			IEclipsePreferences node = this;
			for (int i = 2; i < segmentCount; i++)
				node = (EclipsePreferences) node.parent();
			loadLevel = node;
		}
		return loadLevel;
	}

	protected void initializeChildren() {
		if (initialized || parent == null)
			return;
		try {
			synchronized (this) {
				if (baseLocation == null)
					return;
				String[] names = computeChildren(baseLocation);
				for (int i = 0; i < names.length; i++)
					addChild(names[i], null);
			}
		} finally {
			initialized = true;
		}
	}

	protected EclipsePreferences internalCreate(EclipsePreferences nodeParent, String nodeName, Object context) {
		return new OrionPreferencesLocal(nodeParent, nodeName);
	}

}
