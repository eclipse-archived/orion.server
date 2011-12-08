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

import java.io.IOException;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;

public class ApplyError {

	private FileHeader fileHeader;

	private ChangeType changeType;

	private HunkControlChar hunkContorlChar;

	private HunkHeader hunkHeader;

	private IOException ioException;

	/**
	 * @param fh
	 *            file header
	 * @param t
	 *            change type
	 * @param t
	 *            hunk control char
	*/
	public ApplyError(FileHeader fh, ChangeType t, HunkControlChar hcc) {
		this.fileHeader = fh;
		this.changeType = t;
		this.hunkContorlChar = hcc;
	}

	/**
	 * @param hh
	 *            hunk header
	 * @param t
	 *            change type
	 * @param t
	 *           hunk control char
	*/
	public ApplyError(FileHeader fh, ChangeType t) {
		this(fh, t, null);

	}

	/**
	 * @param hh
	 *            hunk header
	 * @param t
	 *           hunk control char
	*/
	public ApplyError(HunkHeader hh, HunkControlChar hcc) {
		this(hh.getFileHeader(), ChangeType.MODIFY, hcc);
		this.hunkHeader = hh;
	}

	/**
	 * @param e IO exception
	 */
	public ApplyError(IOException e) {
		this.ioException = e;
	}

	/**
	 * @return file header
	 */
	public FileHeader getFileHeader() {
		return fileHeader;
	}

	/**
	 * @return hunk header
	 */
	public HunkHeader getHunkHeader() {
		return hunkHeader;
	}

	/**
	 * @return change type
	 */
	public ChangeType getChangeType() {
		return changeType;
	}

	/**
	 * @return hunk control char
	 */
	public HunkControlChar getHunkControlChar() {
		return hunkContorlChar;
	}

	@Override
	public String toString() {
		final StringBuilder r = new StringBuilder();
		if (ioException != null) {
			r.append(ioException.getMessage());
		} else {
			r.append(getChangeType().name());
			if (getHunkControlChar() != null) {
				r.append(","); //$NON-NLS-1$
				r.append(getHunkControlChar().name());
			}
			r.append(": at hunk "); //$NON-NLS-1$
			r.append(toString(getHunkHeader()));
			r.append("  in "); //$NON-NLS-1$
			r.append(getFileHeader());
		}
		return r.toString();
	}

	private String toString(HunkHeader hh) {
		StringBuilder buf = new StringBuilder();
		buf.append("HunkHeader["); //$NON-NLS-1$
		buf.append(hh.getOldImage().getStartLine() + "," + hh.getOldImage().getLineCount()); //$NON-NLS-1$
		buf.append("->"); //$NON-NLS-1$
		buf.append(hh.getNewStartLine() + "," + hh.getNewLineCount()); //$NON-NLS-1$
		buf.append("]"); //$NON-NLS-1$
		return buf.toString();
	}
}
