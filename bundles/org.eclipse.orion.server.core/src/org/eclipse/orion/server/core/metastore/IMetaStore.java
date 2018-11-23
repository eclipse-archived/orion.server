/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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

import org.eclipse.core.filesystem.IFileStore;
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
	 * Creates a new project in the backing store containing the provided project information.
	 * <p>
	 * It is required the the project information contains the workspaceId of the workspace that owns the
	 * project and a project name.
	 * </p>
	 * @param info the information about the project to create
	 * @throws CoreException if the project could not be created
	 */
	public void createProject(ProjectInfo info) throws CoreException;

	/**
	 * Creates a new user in the backing store containing the provided user information.
	 * @param info the information about the user to create
	 * @throws CoreException if the user could not be created
	 */
	public void createUser(UserInfo info) throws CoreException;

	/**
	 * Creates a new workspace in the backing store containing the provided workspace information.
	 * <p>
	 * It is required the the workspace information contains the userId of the user that owns the
	 * workspace and a workspace name.
	 * </p>
	 * @param info the information about the workspace to create
	 * @throws CoreException if the workspace could not be created
	 */
	public void createWorkspace(WorkspaceInfo info) throws CoreException;

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
	 * Deletes the workspace metadata corresponding to the given information. All artifacts in this store
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
	 * Returns the default local location where user data files for the given project would be stored. 
	 * @return The file store for the given project.
	 */
	public IFileStore getDefaultContentLocation(ProjectInfo projectInfo) throws CoreException;

	/**
	 * Returns the root location where user data files for the given user are stored. 
	 * @return The file store for the given user.
	 */
	public IFileStore getUserHome(String userId);

	/**
	 * Returns the location for the the given workspace. 
	 * @return The file store for the given workspace.
	 */
	public IFileStore getWorkspaceContentLocation(String workspaceId) throws CoreException;

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
	 * If there is no such user in the backing store, create the user.
	 * @param userId The unique id of the user to return
	 * @return the user information or <code>null</code>
	 * @throws CoreException If there was a failure obtaining metadata from the backing store.
	 */
	public UserInfo readUser(String userId) throws CoreException;

	/**
	 * Finds a single user in the backing storage based on a property and returns it.
	 * If there is no such user in the backing store, return <code>null</code>.
	 * It is expected that the property was registered using the {@link #registerUserProperty(String) registerUserProperty} method. 
	 * @param key The property key.
	 * @param value The property value or regular expression to match.
	 * @param regExp <code>true</code> if <code>value</code> should be matched as regular expression.
	 * @param ignoreCase <code>true</code> if <code>value</code> should be matched without case sentitivity.
	 * @return the user matching the criteria or <code>null</code>
	 * @throws CoreException If there was a failure obtaining metadata from the backing store.
	 */
	public UserInfo readUserByProperty(String key, String value, boolean regExp, boolean ignoreCase) throws CoreException;

	/**
	 * Obtains information about a single workspace from the backing storage and returns it.
	 * Returns <code>null</code> if there is no such workspace in the backing store.
	 * @param workspaceId The unique id of the workspace to return
	 * @return the workspace information or <code>null</code>
	 * @throws CoreException If there was a failure obtaining metadata from the backing store.
	 */
	public WorkspaceInfo readWorkspace(String workspaceId) throws CoreException;

	/**
	 * Registers a list of property keys that will be used by the {@link #readUserByProperty(String, String, boolean, boolean) readUserByProperty} 
	 * method to find users in the backing store. 
	 * @param keys A list of property keys.
	 * @throws CoreException If there was a failure obtaining metadata from the backing store.
	 */
	public void registerUserProperties(List<String> keys) throws CoreException;

	/**
	 * Updates the metadata in this store based on the provided data.
	 * @param project The new project data
	 * @throws CoreException If the new data could not be stored, or if
	 * no such project exists
	 */
	public void updateProject(ProjectInfo project) throws CoreException;

	/**
	 * Updates the metadata in this store based on the provided data.
	 * @param info The new user data
	 * @throws CoreException If the new data could not be stored, or if
	 * no such user exists
	 */
	public void updateUser(UserInfo info) throws CoreException;

	/**
	 * Updates the metadata in this store based on the provided data.
	 * @param info The new workspace data
	 * @throws CoreException If the new data could not be stored, or if
	 * no such workspace exists
	 */
	public void updateWorkspace(WorkspaceInfo info) throws CoreException;
}
