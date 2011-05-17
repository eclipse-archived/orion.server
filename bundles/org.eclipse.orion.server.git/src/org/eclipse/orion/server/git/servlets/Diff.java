/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Johannes E. Schindelin
 * Copyright (C) 2009, Johannes Schindelin <johannes.schindelin@gmx.de>
 * Copyright (C) 2011, IBM Corporation
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.orion.server.git.servlets;

import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.*;
import java.util.List;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.treewalk.*;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * @see org.eclipse.jgit.pgm.Diff
 *
 */
public class Diff {
	private final DiffFormatter diffFmt;
	private Repository db;
	private AbstractTreeIterator oldTree;
	private AbstractTreeIterator newTree;
	private boolean cached;
	private TreeFilter pathFilter = TreeFilter.ALL;
	private Boolean detectRenames;
	private Integer renameLimit;
	private boolean showNameAndStatusOnly;

	private PrintWriter out;

	public Diff(OutputStream outputStream) {
		diffFmt = new DiffFormatter(new BufferedOutputStream(outputStream));
		out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream)));
	}

	void setRepository(Repository r) {
		db = r;
	}

	void setCached(boolean cached) {
		this.cached = cached;
	}

	void setPathFilter(TreeFilter pf) {
		pathFilter = pf;
	}

	void setOldTree(AbstractTreeIterator oldTree) {
		this.oldTree = oldTree;
	}

	void setNewTree(AbstractTreeIterator newTree) {
		this.newTree = newTree;
	}

	protected void run() throws Exception {
		diffFmt.setRepository(db);
		try {
			if (cached) {
				if (oldTree == null) {
					ObjectId head = db.resolve(HEAD + "^{tree}"); //$NON-NLS-1$
					if (head == null)
						throw new IllegalArgumentException(HEAD + " is not a tree"); //$NON-NLS-1$
					CanonicalTreeParser p = new CanonicalTreeParser();
					ObjectReader reader = db.newObjectReader();
					try {
						p.reset(reader, head);
					} finally {
						reader.release();
					}
					oldTree = p;
				}
				newTree = new DirCacheIterator(db.readDirCache());
			} else if (oldTree == null) {
				oldTree = new DirCacheIterator(db.readDirCache());
				newTree = new FileTreeIterator(db);
			} else if (newTree == null)
				newTree = new FileTreeIterator(db);

			diffFmt.setProgressMonitor(new TextProgressMonitor());
			diffFmt.setPathFilter(pathFilter);
			if (detectRenames != null)
				diffFmt.setDetectRenames(detectRenames.booleanValue());
			if (renameLimit != null && diffFmt.isDetectRenames()) {
				RenameDetector rd = diffFmt.getRenameDetector();
				rd.setRenameLimit(renameLimit.intValue());
			}

			if (showNameAndStatusOnly) {
				nameStatus(out, diffFmt.scan(oldTree, newTree));
				out.flush();
			} else {
				diffFmt.format(oldTree, newTree);
				diffFmt.flush();
			}
		} finally {
			diffFmt.release();
		}
	}

	static void nameStatus(PrintWriter out, List<DiffEntry> files) {
		for (DiffEntry ent : files) {
			switch (ent.getChangeType()) {
				case ADD :
					out.println("A\t" + ent.getNewPath()); //$NON-NLS-1$
					break;
				case DELETE :
					out.println("D\t" + ent.getOldPath()); //$NON-NLS-1$
					break;
				case MODIFY :
					out.println("M\t" + ent.getNewPath()); //$NON-NLS-1$
					break;
				case COPY :
					out.format("C%1$03d\t%2$s\t%3$s", ent.getScore(), //$NON-NLS-1$
							ent.getOldPath(), ent.getNewPath());
					out.println();
					break;
				case RENAME :
					out.format("R%1$03d\t%2$s\t%3$s", ent.getScore(), //$NON-NLS-1$
							ent.getOldPath(), ent.getNewPath());
					out.println();
					break;
			}
		}
	}
}
