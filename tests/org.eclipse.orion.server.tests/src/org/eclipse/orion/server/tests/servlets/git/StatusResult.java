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

	final public static StatusResult CLEAN = new StatusResult();

	private int added = 0;
	private String[] changedNames;
	private int changed = 0;
	private String[] conflictingNames;
	private int conflicting = 0;
	private int missing = 0;
	private String[] missingNames;
	private int modified = 0;
	private String[] modifiedNames;
	private String[] modifiedPaths;
	private int removed = 0;
	private int untracked = 0;

	public StatusResult setAdded(int added) {
		this.added = added;
		return this;
	}

	public int getAdded() {
		return added;
	}

	public StatusResult setChangedNames(String... changedNames) {
		this.changedNames = changedNames;
		return this;
	}

	public String[] getChangedNames() {
		return changedNames;
	}

	public StatusResult setChanged(int changed) {
		this.changed = changed;
		return this;
	}

	public int getChanged() {
		if (changedNames != null)
			return changedNames.length;
		return changed;
	}

	public StatusResult setConflictingNames(String... conflictingNames) {
		this.conflictingNames = conflictingNames;
		return this;
	}

	public String[] getConflictingNames() {
		return conflictingNames;
	}

	public int getConflicting() {
		if (conflictingNames != null)
			return conflictingNames.length;
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

	public StatusResult setModifiedNames(String... modifiedNames) {
		this.modifiedNames = modifiedNames;
		return this;
	}

	public String[] getModifiedNames() {
		return modifiedNames;
	}

	public StatusResult setModifiedPaths(String... modifiedPaths) {
		this.modifiedPaths = modifiedPaths;
		return this;
	}

	public String[] getModifiedPaths() {
		return modifiedPaths;
	}

	public StatusResult setModified(int modified) {
		this.modified = modified;
		return this;
	}

	public int getModified() {
		if (modifiedNames != null)
			return modifiedNames.length;
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
