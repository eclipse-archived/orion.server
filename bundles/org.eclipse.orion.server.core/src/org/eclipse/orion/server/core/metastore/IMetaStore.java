/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core.metastore;

import java.net.URI;
import org.eclipse.core.runtime.CoreException;

/**
 * The metadata store is responsible for persisting Orion user and workspace metadata. The
 * key design characteristics are:
 * <ul>
 * <li>Each operation on this interface is atomic, it will either completely succeed or completely
 * fail and make no backing change.</li>
 * <li>The store can change in arbitrary ways between any two method invocations. Other
 * threads or other processes can alter the backing store before or after each interaction.</li>
 * </ul>
 * @noimplement This is not intended to be implemented by clients. Backing store implementations
 * will implement this interface, but must expect breaking changes to occur between releases.
 */
public interface IMetaStore {

	/**
	 * Creates a new user in the backing store containing the provided user information.
	 * @param info
	 * @throws CoreException
	 */
	public void createUser(UserInfo info) throws CoreException;

	public void createWorkspace(WorkspaceInfo info) throws CoreException;

	public void createProject(ProjectInfo info) throws CoreException;

	public UserInfo readUser(String uid) throws CoreException;

	public void updateUser(UserInfo info) throws CoreException;

	public void deleteUser(URI location) throws CoreException;
}
