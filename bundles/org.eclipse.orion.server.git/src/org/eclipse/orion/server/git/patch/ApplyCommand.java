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
package org.eclipse.orion.server.git.patch;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.*;
import org.eclipse.jgit.util.IO;
import org.eclipse.orion.internal.server.core.IOUtilities;

/**
 * Apply a patch to files and/or to the index.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-apply.html"
 *      >Git documentation about apply</a>
 */
public class ApplyCommand extends GitCommand<ApplyResult> {

	private InputStream in;

	/**
	 * Constructs the command if the patch is to be applied to the index.
	 *
	 * @param repo
	 */
	public ApplyCommand(Repository repo) {
		super(repo);
	}

	/**
	 * @param in
	 *            the patch to apply
	 * @return this instance
	 */
	public ApplyCommand setPatch(InputStream in) {
		this.in = in;
		return this;
	}

	/**
	 * Executes the {@code ApplyCommand} command with all the options and
	 * parameters collected by the setter methods (e.g.
	 * {@link #setPatch(InputStream)} of this class. Each instance of this class
	 * should only be used for one invocation of the command. Don't call this
	 * method twice on an instance.
	 *
	 * @return collection of formatting errors, if any
	 */
	public ApplyResult call() {
		ApplyResult r = new ApplyResult();
		try {
			final Patch p = new Patch();
			p.parse(in);
			if (!p.getErrors().isEmpty())
				return r.setFormatErrors(p.getErrors());
			for (FileHeader fh : p.getFiles()) {
				ChangeType type = fh.getChangeType();
				File f = null;
				switch (type) {
					case ADD :
						f = getFile(fh.getNewPath(), true);
						apply(f, fh, r);
						break;
					case MODIFY :
						f = getFile(fh.getOldPath(), false);
						apply(f, fh, r);
						break;
					case DELETE :
						f = getFile(fh.getOldPath(), false);
						if (!f.delete())
							r.addApplyError(new ApplyError(fh, ChangeType.DELETE));
						break;
					case RENAME :
						f = getFile(fh.getOldPath(), false);
						File dest = getFile(fh.getNewPath(), false);
						if (!f.renameTo(dest))
							r.addApplyError(new ApplyError(fh, ChangeType.RENAME));
						break;
					case COPY :
						f = getFile(fh.getOldPath(), false);
						byte[] bs = IO.readFully(f);
						FileWriter fw = new FileWriter(getFile(fh.getNewPath(), true));
						fw.write(new String(bs));
						fw.close();
				}
			}
		} catch (IOException e) {
			r.addApplyError(new ApplyError(e));
		} finally {
			IOUtilities.safeClose(in);
		}
		return r;
	}

	private File getFile(String path, boolean create) throws IOException {
		File f = new File(getRepository().getWorkTree(), path);
		if (create)
			f.createNewFile();
		return f;
	}

	private void apply(File f, FileHeader fh, ApplyResult r) throws IOException {
		RawText rt = new RawText(f);
		List<String> oldLines = new ArrayList<String>(rt.size());
		for (int i = 0; i < rt.size(); i++) {
			oldLines.add(rt.getString(i));
		}
		List<String> newLines = new ArrayList<String>(oldLines);
		HUNKS: for (HunkHeader hh : fh.getHunks()) {
			StringBuilder hunk = new StringBuilder();
			for (int j = hh.getStartOffset(); j < hh.getEndOffset(); j++) {
				hunk.append((char) hh.getBuffer()[j]);
			}
			RawText hrt = new RawText(hunk.toString().getBytes());
			List<String> hunkLines = new ArrayList<String>(hrt.size());
			for (int i = 0; i < hrt.size(); i++) {
				hunkLines.add(hrt.getString(i));
			}
			int pos = 0;
			for (int j = 1; j < hunkLines.size(); j++) {
				String hunkLine = hunkLines.get(j);
				switch (HunkControlChar.valueOf(hunkLine.charAt(0))) {
					case CONTEXT :
						if (!newLines.get(hh.getNewStartLine() - 1 + pos).equals(hunkLine.substring(1))) {
							r.addApplyError(new ApplyError(hh, HunkControlChar.CONTEXT));
							continue HUNKS;
						}
						pos++;
						break;
					case REMOVE :
						if (!newLines.get(hh.getNewStartLine() - 1 + pos).equals(hunkLine.substring(1))) {
							r.addApplyError(new ApplyError(hh, HunkControlChar.REMOVE));
							continue HUNKS;
						}
						newLines.remove(hh.getNewStartLine() - 1 + pos);
						break;
					case ADD :
						newLines.add(hh.getNewStartLine() - 1 + pos, hunkLine.substring(1));
						pos++;
						break;
				}
			}
		}
		if (!isChanged(oldLines, newLines))
			return; // don't touch the file

		StringBuilder sb = new StringBuilder();
		for (String l : newLines) {
			sb.append(l).append("\n"); // TODO: proper line ending
		}
		if (isNoNewlineAtEndOfFile(fh))
			sb.deleteCharAt(sb.length() - 1);
		FileWriter fw = new FileWriter(f);
		fw.write(sb.toString());
		fw.close();
	}

	private boolean isChanged(List<String> ol, List<String> nl) {
		if (ol.size() != nl.size())
			return true;
		for (int i = 0; i < ol.size(); i++) {
			if (!ol.get(i).equals(nl.get(i)))
				return true;
		}
		return false;
	}

	private boolean isNoNewlineAtEndOfFile(FileHeader fh) {
		HunkHeader lastHunk = fh.getHunks().get(fh.getHunks().size() - 1);
		RawText lhrt = new RawText(lastHunk.getBuffer());
		return lhrt.getString(lhrt.size() - 1).equals("\\ No newline at end of file"); //$NON-NLS-1$
	}
}
