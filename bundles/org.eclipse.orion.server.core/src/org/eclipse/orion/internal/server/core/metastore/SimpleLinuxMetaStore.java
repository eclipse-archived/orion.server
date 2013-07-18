package org.eclipse.orion.internal.server.core.metastore;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.ServerConstants;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.ProjectInfo;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.json.JSONException;
import org.json.JSONObject;

public class SimpleLinuxMetaStore implements IMetaStore {

	public final static String NAME = "orion.metastore.version";

	public final static int VERSION = 1;

	private URI metaStoreRoot = null;

	public SimpleLinuxMetaStore(URI rootLocation) {
		super();
		initializeMetaStore(rootLocation);
	}

	private void initializeMetaStore(URI rootLocation) {
		if (!SimpleLinuxMetaStoreUtil.isMetaStoreRoot(rootLocation)) {
			// Create a new MetaStore
			JSONObject jsonObject = new JSONObject();
			try {
				jsonObject.put(NAME, VERSION);
			} catch (JSONException e) {
			}
			if (!SimpleLinuxMetaStoreUtil.createMetaStoreRoot(rootLocation, jsonObject)) {
				throw new IllegalArgumentException("SimpleLinuxMetaStore.initializeMetaStore: could not create MetaStore");
			}
		} else {
			// Verify we have a MetaStore
			JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaStoreRoot(rootLocation);
			try {
				if (jsonObject == null || jsonObject.getInt(NAME) != VERSION) {
					throw new IllegalArgumentException("SimpleLinuxMetaStore.initializeMetaStore: could not read MetaStore");
				}
			} catch (JSONException e) {
			}
		}
		try {
			this.metaStoreRoot = new URI(rootLocation.toString() + SimpleLinuxMetaStoreUtil.ROOT);
		} catch (URISyntaxException e) {
		}
	}

	public void createProject(String workspaceId, ProjectInfo info) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void createUser(UserInfo userInfo) throws CoreException {
		if (!SimpleLinuxMetaStoreUtil.isNameValid(userInfo.getUserName())) {
			throw new IllegalArgumentException("SimpleLinuxMetaStore.createUser: userName is not valid: " + userInfo.getUserName());
		}
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put(NAME, VERSION);
			jsonObject.put("Id", userInfo.getUserName());
			jsonObject.put("UserName", userInfo.getUserName());
		} catch (JSONException e) {
		}
		if (!SimpleLinuxMetaStoreUtil.createMetaFile(metaStoreRoot, userInfo.getUserName(), jsonObject)) {
			throw new IllegalArgumentException("SimpleLinuxMetaStore.createUser: could not create user " + userInfo.getUserName());
		}
	}

	public void createWorkspace(String userId, WorkspaceInfo info) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void deleteProject(String workspaceId, String projectName) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void deleteUser(String userId) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void deleteWorkspace(String userId, String workspaceId) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public List<String> readAllUsers() throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public ProjectInfo readProject(String workspaceId, String projectName) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public UserInfo readUser(String userId) throws CoreException {
		JSONObject jsonObject = SimpleLinuxMetaStoreUtil.retrieveMetaFile(metaStoreRoot, userId);
		if (jsonObject == null) {
			throw new IllegalArgumentException("SimpleLinuxMetaStore.createUser: could not read user " + userId);
		}
		UserInfo userInfo = new UserInfo();
		try {
			userInfo.setUniqueId(jsonObject.getString("Id"));
			userInfo.setUserName(jsonObject.getString("UserName"));
		} catch (JSONException e) {
		}
		return userInfo;
	}

	public WorkspaceInfo readWorkspace(String workspaceId) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void updateProject(ProjectInfo project) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void updateWorkspace(WorkspaceInfo info) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

	public void updateUser(UserInfo info) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ServerConstants.PI_SERVER_CORE, 1, "Not Implemented", null));
	}

}
