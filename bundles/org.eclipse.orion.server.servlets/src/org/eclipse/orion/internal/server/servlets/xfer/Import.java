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

import java.io.*;
import java.util.Properties;
import org.eclipse.core.runtime.IPath;
import org.osgi.framework.FrameworkUtil;

/**
 * Represents an import operation in progress.
 */
class Import {

	/**
	 * The UUID of this import operation.
	 */
	private final String id;

	private Properties props = new Properties();

	Import(String id) {
		this.id = id;

	}

	private File getStorageDirectory() {
		return FrameworkUtil.getBundle(Import.class).getDataFile("xfer/" + id);
	}

	void save() throws IOException {
		File dir = getStorageDirectory();
		dir.mkdirs();
		File index = new File(dir, "xfer.properties");
		props.store(new FileWriter(index), null);
	}

	public void setFileName(String name) {
		props.put("FileName", name);
	}

	/**
	 * Sets the total length of the file being imported.
	 */
	public void setLength(long length) {
		props.put("Length", Long.toString(length));
	}

	/**
	 * Sets the path of the file in the workspace once the import completes.
	 */
	public void setPath(IPath path) {
		props.put("Path", path.toString());
	}

}
