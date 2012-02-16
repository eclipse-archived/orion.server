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

import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.*;
import org.eclipse.jgit.util.FileUtils;
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
		checkCallable();
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
	public ApplyResult call() throws Exception {
		checkCallable();
		ApplyResult r = new ApplyResult();
		try {
			final Patch p = new Patch();
			try {
				p.parse(in);
			} finally {
				in.close();
			}
			if (!p.getErrors().isEmpty())
				throw new PatchFormatException(p.getErrors());
			for (FileHeader fh : p.getFiles()) {
				ChangeType type = fh.getChangeType();
				File f = null;
				switch (type) {
					case ADD :
						f = getFile(fh.getNewPath(), true);
						apply(f, fh);
						break;
					case MODIFY :
						f = getFile(fh.getOldPath(), false);
						apply(f, fh);
						break;
					case DELETE :
						f = getFile(fh.getOldPath(), false);
						if (!f.delete())
							throw new PatchApplyException(MessageFormat.format(JGitText.get().cannotDeleteFile, f));
						break;
					case RENAME :
						f = getFile(fh.getOldPath(), false);
						File dest = getFile(fh.getNewPath(), false);
						if (!f.renameTo(dest))
							throw new PatchApplyException(MessageFormat.format("Could not rename file {0} to {1}", f, dest));
						break;
					case COPY :
						f = getFile(fh.getOldPath(), false);
						byte[] bs = IO.readFully(f);
						FileWriter fw = new FileWriter(getFile(fh.getNewPath(), true));
						fw.write(new String(bs));
						fw.close();
				}
				r.addUpdatedFile(f);
			}
		} catch (IOException e) {
			throw new PatchApplyException(MessageFormat.format("Cannot apply: {0}", e.getMessage()), e);
		} finally {
			IOUtilities.safeClose(in);
		}
		setCallable(false);
		return r;
	}

	private File getFile(String path, boolean create) throws IOException, PatchApplyException {
		File f = new File(getRepository().getWorkTree(), path);
		if (create)
			try {
				FileUtils.createNewFile(f);
			} catch (IOException e) {
				throw new PatchApplyException(MessageFormat.format("Could not create new file {0}", f), e);
			}
		return f;
	}

	private void apply(File f, FileHeader fh) throws IOException, PatchApplyException {
		OrionRawText rt = new OrionRawText(f);
		List<String> oldLines = new ArrayList<String>(rt.size());
		for (int i = 0; i < rt.size(); i++)
			oldLines.add(rt.getString(i));
		List<String> newLines = new ArrayList<String>(oldLines);
		for (HunkHeader hh : fh.getHunks()) {
			StringBuilder hunk = new StringBuilder();
			for (int j = hh.getStartOffset(); j < hh.getEndOffset(); j++)
				hunk.append((char) hh.getBuffer()[j]);
			RawText hrt = new OrionRawText(hunk.toString().getBytes());
			List<String> hunkLines = new ArrayList<String>(hrt.size());
			for (int i = 0; i < hrt.size(); i++)
				hunkLines.add(hrt.getString(i));
			int pos = 0;
			for (int j = 1; j < hunkLines.size(); j++) {
				String hunkLine = hunkLines.get(j);
				switch (HunkControlChar.valueOf(hunkLine.charAt(0))) {
					case CONTEXT :
						if (!newLines.get(hh.getNewStartLine() - 1 + pos).equals(hunkLine.substring(1))) {
							throw new PatchApplyException(MessageFormat.format("Cannot apply: {0}", hh));
						}
						pos++;
						break;
					case REMOVE :
						if (!newLines.get(hh.getNewStartLine() - 1 + pos).equals(hunkLine.substring(1))) {
							throw new PatchApplyException(MessageFormat.format("Cannot apply: {0}", hh));
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
		if (!isNoNewlineAtEndOfFile(fh))
			newLines.add(""); //$NON-NLS-1$
		if (!rt.isMissingNewlineAtEnd())
			oldLines.add(""); //$NON-NLS-1$
		if (!isChanged(oldLines, newLines))
			return; // don't touch the file
		StringBuilder sb = new StringBuilder();
		final String eol = rt.size() == 0 || (rt.size() == 1 && rt.isMissingNewlineAtEnd()) ? "\n" : rt.getEOL(); //$NON-NLS-1$
		for (String l : newLines) {
			sb.append(l);
			if (eol != null)
				sb.append(eol);
		}
		sb.deleteCharAt(sb.length() - 1);
		FileWriter fw = new FileWriter(f);
		fw.write(sb.toString());
		fw.close();
	}

	private boolean isChanged(List<String> ol, List<String> nl) {
		if (ol.size() != nl.size())
			return true;
		for (int i = 0; i < ol.size(); i++)
			if (!ol.get(i).equals(nl.get(i)))
				return true;
		return false;
	}

	private boolean isNoNewlineAtEndOfFile(FileHeader fh) {
		HunkHeader lastHunk = fh.getHunks().get(fh.getHunks().size() - 1);
		RawText lhrt = new RawText(lastHunk.getBuffer());
		return lhrt.getString(lhrt.size() - 1).equals("\\ No newline at end of file"); //$NON-NLS-1$
	}

	private class OrionRawText extends RawText {
		public OrionRawText(File f) throws IOException {
			super(f);
		}

		public OrionRawText(byte[] bytes) {
			super(bytes);
		}

		private int getEnd(final int i) {
			return lines.get(i + 2);
		}

		private int getStart(final int i) {
			return lines.get(i + 1);
		}

		@Override
		public String getString(int begin, int end, boolean dropLineEnding) {
			if (begin == end)
				return ""; //$NON-NLS-1$

			int s = getStart(begin);
			int e = getEnd(end - 1);
			if (dropLineEnding && content.length > 1 && content[e - 2] == '\r' && content[e - 1] == '\n')
				e = e - 2;
			else if (dropLineEnding && content[e - 1] == '\n')
				e--;
			return decode(s, e);
		}

		// see bug 370320
		public String getEOL() {
			int e = getEnd(0);
			if (content.length > 1 && content[e - 2] == '\r' && content[e - 1] == '\n')
				return "\r\n"; //$NON-NLS-1$
			if (content.length > 0 && content[e - 1] == '\n')
				return "\n"; //$NON-NLS-1$
			return null;
		}
	}
}
