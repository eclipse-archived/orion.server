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

import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
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
		out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
				outputStream)));
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

	protected void run() throws Exception {
		diffFmt.setRepository(db);
		try {
			if (cached) {
				if (oldTree == null) {
					ObjectId head = db.resolve(HEAD + "^{tree}");
					if (head == null)
						throw new IllegalArgumentException(HEAD
								+ "is not a tree");
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
			case ADD:
				out.println("A\t" + ent.getNewPath());
				break;
			case DELETE:
				out.println("D\t" + ent.getOldPath());
				break;
			case MODIFY:
				out.println("M\t" + ent.getNewPath());
				break;
			case COPY:
				out.format("C%1$03d\t%2$s\t%3$s", ent.getScore(),
						ent.getOldPath(), ent.getNewPath());
				out.println();
				break;
			case RENAME:
				out.format("R%1$03d\t%2$s\t%3$s", ent.getScore(),
						ent.getOldPath(), ent.getNewPath());
				out.println();
				break;
			}
		}
	}
}
