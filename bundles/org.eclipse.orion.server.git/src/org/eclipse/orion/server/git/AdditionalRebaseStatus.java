/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git;

public enum AdditionalRebaseStatus {
	/**
	 * Failed due to an invalid repository state; action started earlier should be finished (i.e. rebasing, merging etc.)
	 */
	FAILED_WRONG_REPOSITORY_STATE,
	/**
	 * Failed due to unmerged paths; conflicts should be resolved before rebase can be continued
	 */
	FAILED_UNMERGED_PATHS,
	/**
	 * Failed due to pending changes (unstaged or uncommited); changes should be commited or stashed
	 */
	FAILED_PENDING_CHANGES;
}
