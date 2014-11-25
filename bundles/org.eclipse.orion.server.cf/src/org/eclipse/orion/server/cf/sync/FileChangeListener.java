/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.sync;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.server.core.events.IFileChangeListener;

public class FileChangeListener implements IFileChangeListener {

	@Override
	public void directoryCreated(IFileStore directory) {
		// TODO Auto-generated method stub

	}

	@Override
	public void directoryDeleted(IFileStore directory) {
		// TODO Auto-generated method stub

	}

	@Override
	public void directoryUpdated(IFileStore directory) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fileCreated(IFileStore file) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fileDeleted(IFileStore file) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fileUpdated(IFileStore file) {
		// TODO Auto-generated method stub
		System.out.println(file);
	}
}
