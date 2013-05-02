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

import java.util.List;
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
	 * Creates a new user in the backing store containing the provided project information.
	 * @param info the information about the project to create
	 * @throws CoreException if the project could not be created
	 */
	public void createProject(String workspaceId, ProjectInfo info) throws CoreException;

	/**
	 * Creates a new user in the backing store containing the provided user information.
	 * @param info the information about the user to create
	 * @throws CoreException if the user could not be created
	 */
	public void createUser(UserInfo info) throws CoreException;

	/**
	 * Creates a new workspace in the backing store containing the provided workspace information.
	 * @param info the information about the workspace to create
	 * @throws CoreException if the workspace could not be created
	 */
	public void createWorkspace(String userId, WorkspaceInfo info) throws CoreException;

	/**
	 * Deletes the project metadata corresponding to the given project name in the given
	 * workspace.
	 * <p>
	 * If no such project exists, this method has no effect.
	 * </p>
	 * @param workspaceId the unique id of the workspace containing the project
	 * @param projectName the full name of the project to delete
	 * @throws CoreException If the project could not be deleted
	 */
	public void deleteProject(String workspaceId, String projectName) throws CoreException;

	/**
	 * Deletes the user metadata corresponding to the given id. All artifacts in the backing store
	 * uniquely owned by this user are also deleted from the backing store (such as workspaces
	 * and projects).
	 * <p>
	 * If no such user exists, this method has no effect. 
	 * </p>
	 * @param userId The id of the user to delete
	 * @throws CoreException If the user could not be deleted.
	 */
	public void deleteUser(String userId) throws CoreException;

	/**
	 * Deletes the workspace metadata corresponding to the given id. All artifacts in this store
	 * uniquely owned by this user are also deleted from the backing store (such as projects).
	 * <p>
	 * If no such workspace exists, this method has no effect. 
	 * </p>
	 * @param userId The id of the user to delete the workspace for
	 * @param workspaceId The id of the workspace to delete
	 * @throws CoreException If the workspace could not be deleted
	 */
	public void deleteWorkspace(String userId, String workspaceId) throws CoreException;

	/**
	 * Returns a list of all user ids in this store. Manipulating the returned
	 * list is not supported. Adding or removing users can only be achieved using
	 * {@link #createUser(UserInfo)} and {@link #deleteUser(String)}.
	 * @return a list of all user ids
	 */
	public List<String> readAllUsers() throws CoreException;

	/**
	 * Obtains information about a single project from this store and returns it.
	 * Returns <code>null</code> if there is no such project in the metadata store.
	 * @param workspaceId The unique id of the workspace containing the project
	 * @param projectName The full name of the project
	 * @return the project information, or <code>null</code>
	 * @throws CoreException If there was a failure obtaining metadata from the backing store.
	 */
	public ProjectInfo readProject(String workspaceId, String projectName) throws CoreException;

	/**
	 * Obtains information about a single user from the backing storage and returns it.
	 * Returns <code>null</code> if there is no such project in the backing store.
	 * @param userId The unique id of the user to return
	 * @return the user information or <code>null</code>
	 * @throws CoreException If there was a failure obtaining metadata from the backing store.
	 */
	public UserInfo readUser(String userId) throws CoreException;

	/**
	 * Obtains information about a single workspace from the backing storage and returns it.
	 * Returns <code>null</code> if there is no such workspace in the backing store.
	 * @param workspaceId The unique id of the workspace to return
	 * @return the workspace information or <code>null</code>
	 * @throws CoreException If there was a failure obtaining metadata from the backing store.
	 */
	public WorkspaceInfo readWorkspace(String workspaceId) throws CoreException;

	/**
	 * Updates the metadata in this store based on the provided data.
	 * @param project The new project data
	 * @throws CoreException If the new data could not be stored, or if
	 * no such project  exists
	 */
	public void updateProject(ProjectInfo project) throws CoreException;

	/**
	 * Updates the metadata in this store based on the provided data.
	 * @param info The new user data
	 * @throws CoreException If the new data could not be stored, or if
	 * no such user exists
	 */
	public void updateUser(UserInfo info) throws CoreException;
}
