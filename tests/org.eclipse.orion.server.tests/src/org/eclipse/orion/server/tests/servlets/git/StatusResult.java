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

/**
 * A builder for expected result of calling "git status" over REST API.
 */
public class StatusResult {

	/**
	 * Result representing a clean working directory. Nothing to commit, nothing to stage.
	 */
	final public static StatusResult CLEAN = new StatusResult();

	private int added = 0;
	private String[] addedNames;
	private int[] addedLogLengths;
	private int changed = 0;
	private String[] changedNames;
	private String[] changedContents;
	private String[] changedDiffs;
	private String[] changedIndexContents;
	private String[] changedHeadContents;
	private int[] changedLogLengths;
	private int conflicting = 0;
	private String[] conflictingNames;
	private int missing = 0;
	private String[] missingNames;
	private int[] missingLogLengths;
	private int modified = 0;
	private String[] modifiedNames;
	private String[] modifiedPaths;
	private String[] modifiedContents;
	private String[] modifiedDiffs;
	private int[] modifiedLogLengths;
	private int removed = 0;
	private String[] removedNames;
	private int[] removedLogLengths;
	private int untracked = 0;
	private String[] untrackedNames;
	private int[] untrackedLogLengths;

	public StatusResult setAdded(int added) {
		this.added = added;
		return this;
	}

	public int getAdded() {
		if (addedNames != null)
			return addedNames.length;
		return added;
	}

	public StatusResult setAddedNames(String... addedNames) {
		this.addedNames = addedNames;
		return this;
	}

	public String[] getAddedNames() {
		return addedNames;
	}

	public StatusResult setAddedLogLengths(int... addedLogLengths) {
		if (addedNames == null || addedNames.length != addedLogLengths.length)
			throw new IllegalStateException("addedNames has to be set first");
		this.addedLogLengths = addedLogLengths;
		return this;
	}

	public int[] getAddedLogLengths() {
		return addedLogLengths;
	}

	public StatusResult setChanged(int changed) {
		this.changed = changed;
		return this;
	}

	public int getChanged() {
		if (changedNames != null) {
			return changedNames.length;
		}
		return changed;
	}

	public StatusResult setChangedNames(String... changedNames) {
		this.changedNames = changedNames;
		return this;
	}

	public String[] getChangedNames() {
		return changedNames;
	}

	/**
	 * Ask if a given name is found in the changed name list
	 * @param name The string name
	 * @return A boolean if the given name is in the list or not
	 * @since 14.0
	 */
	public boolean containsChangedName(String name) {
		return arrayContains(this.changedNames, name);
	}
	
	public StatusResult setChangedContents(String... changedContents) {
		if (changedNames == null || changedNames.length != changedContents.length) {
			throw new IllegalStateException("changedNames has to be set first");
		}
		this.changedContents = changedContents;
		return this;
	}

	public String[] getChangedContents() {
		return changedContents;
	}

	/**
	 * Ask if the given contents are found in the changed contents list
	 * @param name The string contents
	 * @return A boolean if the given contents are in the list or not
	 * @since 14.0
	 */
	public boolean containsChangedContent(String contents) {
		return arrayContains(this.changedContents, contents);
	}
	
	public StatusResult setChangedDiffs(String... changedDiffs) {
		if (changedNames == null || changedNames.length != changedDiffs.length)
			throw new IllegalStateException("changedNames has to be set first");
		this.changedDiffs = changedDiffs;
		return this;
	}

	public String[] getChangedDiffs() {
		return changedDiffs;
	}

	public StatusResult setChangedIndexContents(String... changedIndexContents) {
		if (changedNames == null || changedNames.length != changedIndexContents.length)
			throw new IllegalStateException("changedNames has to be set first");
		this.changedIndexContents = changedIndexContents;
		return this;
	}

	public String[] getChangedIndexContents() {
		return changedIndexContents;
	}

	public StatusResult setChangedHeadContents(String... changedHeadContents) {
		if (changedNames == null || changedNames.length != changedHeadContents.length)
			throw new IllegalStateException("changedNames has to be set first");
		this.changedHeadContents = changedHeadContents;
		return this;
	}

	public String[] getChangedHeadContents() {
		return changedHeadContents;
	}

	public StatusResult setChangedLogLengths(int... changedLogLengths) {
		if (changedNames == null || changedNames.length != changedLogLengths.length)
			throw new IllegalStateException("changedNames has to be set first");
		this.changedLogLengths = changedLogLengths;
		return this;
	}

	public int[] getChangedLogLengths() {
		return changedLogLengths;
	}

	public int getConflicting() {
		if (conflictingNames != null)
			return conflictingNames.length;
		return conflicting;
	}

	public StatusResult setConflictingNames(String... conflictingNames) {
		this.conflictingNames = conflictingNames;
		return this;
	}

	public String[] getConflictingNames() {
		return conflictingNames;
	}

	public StatusResult setMissing(int missing) {
		this.missing = missing;
		return this;
	}

	public int getMissing() {
		if (missingNames != null)
			return missingNames.length;
		return missing;
	}

	public StatusResult setMissingNames(String... missingNames) {
		this.missingNames = missingNames;
		return this;
	}

	public String[] getMissingNames() {
		return missingNames;
	}

	public StatusResult setMissingLogLengths(int... missingLogLengths) {
		if (missingNames == null || missingNames.length != missingLogLengths.length)
			throw new IllegalStateException("missingNames has to be set first");
		this.missingLogLengths = missingLogLengths;
		return this;
	}

	public int[] getMissingLogLengths() {
		return missingLogLengths;
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

	public StatusResult setModifiedNames(String... modifiedNames) {
		this.modifiedNames = modifiedNames;
		return this;
	}

	/**
	 * Ask if a given name is found in the modified name list
	 * @param name The string name
	 * @return A boolean if the given name is in the list or not
	 * @since 14.0
	 */
	public boolean containsModifiedName(String name) {
		return arrayContains(this.modifiedNames, name);
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

	/**
	 * Ask if a given path is found in the modified path list
	 * @param name The string path
	 * @return A boolean if the given path is in the list or not
	 * @since 14.0
	 */
	public boolean containsModifiedPath(String path) {
		return arrayContains(this.modifiedPaths, path);
	}
	
	public StatusResult setModifiedContents(String... modifiedContents) {
		if (modifiedNames == null || modifiedNames.length != modifiedContents.length)
			throw new IllegalStateException("modifiedNames has to be set first");
		this.modifiedContents = modifiedContents;
		return this;
	}

	public String[] getModifiedContents() {
		return modifiedContents;
	}

	/**
	 * Ask if the given contents are found in the modified contents list
	 * @param name The string contents
	 * @return A boolean if the given contents are in the list or not
	 * @since 14.0
	 */
	public boolean containsModifiedContent(String contents) {
		return arrayContains(this.modifiedContents, contents);
	}
	
	/**
	 * Delegate method to walk an array looking for a certain value
	 * @param arr The array to check
	 * @param value
	 * @return A boolean if the array contains the given value
	 * @since 14.0
	 */
	private <T> boolean arrayContains(T[] arr, T value) {
		if(arr == null) {
			return false;
		}
		for(T val: arr) {
			if(val.equals(value)) {
				return true;
			}
		}
		return false;
	}
	
	public StatusResult setModifiedDiffs(String... modifiedDiffs) {
		if (modifiedNames == null || modifiedNames.length != modifiedDiffs.length)
			throw new IllegalStateException("modifiedNames has to be set first");
		this.modifiedDiffs = modifiedDiffs;
		return this;
	}

	public String[] getModifiedDiffs() {
		return modifiedDiffs;
	}

	public StatusResult setModifiedLogLengths(int... modifiedLogLengths) {
		if (modifiedNames == null || modifiedNames.length != modifiedLogLengths.length)
			throw new IllegalStateException("modifiedNames has to be set first");
		this.modifiedLogLengths = modifiedLogLengths;
		return this;
	}

	public int[] getModifiedLogLengths() {
		return modifiedLogLengths;
	}

	public StatusResult setRemoved(int removed) {
		this.removed = removed;
		return this;
	}

	public int getRemoved() {
		if (removedNames != null)
			return removedNames.length;
		return removed;
	}

	public StatusResult setRemovedNames(String... removedNames) {
		this.removedNames = removedNames;
		return this;
	}

	public String[] getRemovedNames() {
		return removedNames;
	}

	public StatusResult setRemovedLogLengths(int... removedLogLengths) {
		if (removedNames == null || removedNames.length != removedLogLengths.length)
			throw new IllegalStateException("removedNames has to be set first");
		this.removedLogLengths = removedLogLengths;
		return this;
	}

	public int[] getRemovedLogLengths() {
		return removedLogLengths;
	}

	public StatusResult setUntracked(int untracked) {
		this.untracked = untracked;
		return this;
	}

	public int getUntracked() {
		if (untrackedNames != null)
			return untrackedNames.length;
		return untracked;
	}

	public StatusResult setUntrackedNames(String... untrackedNames) {
		this.untrackedNames = untrackedNames;
		return this;
	}

	public String[] getUntrackedNames() {
		return untrackedNames;
	}

	public StatusResult setUntrackedLogLengths(int... untrackedLogLengths) {
		if (untrackedNames == null || untrackedNames.length != untrackedLogLengths.length)
			throw new IllegalStateException("untrackedNames has to be set first");
		this.untrackedLogLengths = untrackedLogLengths;
		return this;
	}

	public int[] getUntrackedLogLengths() {
		return untrackedLogLengths;
	}
}
