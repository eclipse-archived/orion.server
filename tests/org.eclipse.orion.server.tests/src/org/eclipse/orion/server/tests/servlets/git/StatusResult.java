/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.git;

public class StatusResult {

	private int added = 0;
	private int changed = 0;
	private int conflicting = 0;
	private int missing = 0;
	private String[] missingNames;
	private int modified = 0;
	private int removed = 0;
	private int untracked = 0;

	public StatusResult setAdded(int added) {
		this.added = added;
		return this;
	}

	public int getAdded() {
		return added;
	}

	public StatusResult setChanged(int changed) {
		this.changed = changed;
		return this;
	}

	public int getChanged() {
		return changed;
	}

	public int getConflicting() {
		return conflicting;
	}

	public StatusResult setMissing(int missing) {
		this.missing = missing;
		return this;
	}

	public StatusResult setMissingNames(String... missingNames) {
		this.missingNames = missingNames;
		return this;
	}

	public String[] getMissingNames() {
		return missingNames;
	}

	public int getMissing() {
		if (missingNames != null)
			return missingNames.length;
		return missing;
	}

	public StatusResult setModified(int modified) {
		this.modified = modified;
		return this;
	}

	public int getModified() {
		return modified;
	}

	public StatusResult setRemoved(int removed) {
		this.removed = removed;
		return this;
	}

	public int getRemoved() {
		return removed;
	}

	public StatusResult setUntracked(int untracked) {
		this.untracked = untracked;
		return this;
	}

	public int getUntracked() {
		return untracked;
	}
}
