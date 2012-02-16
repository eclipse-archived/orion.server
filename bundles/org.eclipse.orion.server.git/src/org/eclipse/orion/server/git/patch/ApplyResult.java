/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.patch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the result of a {@link ApplyCommand}
 */
public class ApplyResult {

	private List<File> updatedFiles = new ArrayList<File>();

	/**
	* @param f
	*            an updated file
	* @return this instance
	*/
	public ApplyResult addUpdatedFile(File f) {
		updatedFiles.add(f);
		return this;

	}

	/**
	 * @return updated files
	  */
	public List<File> getUpdatedFiles() {
		return updatedFiles;
	}
}
