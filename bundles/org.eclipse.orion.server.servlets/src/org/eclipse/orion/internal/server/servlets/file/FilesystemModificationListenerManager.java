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
package org.eclipse.orion.internal.server.servlets.file;

import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.orion.internal.server.servlets.IFilesystemModificationListener;

/**
 * Maintains the set of modification listeners. 
 */
public class FilesystemModificationListenerManager {

	private static final Object INSTANCE_LOCK = new Object();

	private static FilesystemModificationListenerManager instance;

	public static final FilesystemModificationListenerManager getInstance() {
		synchronized (INSTANCE_LOCK) {
			if (instance == null) {
				instance = new FilesystemModificationListenerManager();

				instance.init();
			}

			return instance;
		}
	}

	private void init() {
	}

	private ConcurrentHashMap<IFilesystemModificationListener, IFilesystemModificationListener> listeners = new ConcurrentHashMap<IFilesystemModificationListener, IFilesystemModificationListener>();

	/**
	 * Add a listener that will be notified when changes are made through the Orion filesystem 
	 * API. 
	 */
	public void addListener(IFilesystemModificationListener l) {
		listeners.put(l, l);
	}

	/**
	 * Remove a listener previously added by {@link #addListener(IFilesystemModificationListener)}.  
	 */
	public IFilesystemModificationListener removeListener(IFilesystemModificationListener l) {
		if (l == null) {
			return null;
		}

		return listeners.remove(l);
	}

	void notifyOfChange(IFilesystemModificationListener.ChangeEvent event) {
		for (IFilesystemModificationListener l : listeners.keySet()) {
			try {
				l.changed(event);
			} catch (Exception e) {
				// Ignore
			}
		}
	}

	boolean hasListeners() {
		return !listeners.isEmpty();
	}
}
