/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.jobs;

import java.io.IOException;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;

@SuppressWarnings("restriction")
public class GitJobUtils {
	/**
	 * Calls pack refs for a given repository
	 * 
	 * @param Repository
	 *            the git repository
	 * @throws IOException
	 */
	public static void packRefs(Repository repo, ProgressMonitor monitor) throws IOException {
		if (repo != null && repo instanceof FileRepository) {
			GC gc = new GC(((FileRepository) repo));
			gc.setProgressMonitor(monitor);
			gc.packRefs();
		}
	}

}
