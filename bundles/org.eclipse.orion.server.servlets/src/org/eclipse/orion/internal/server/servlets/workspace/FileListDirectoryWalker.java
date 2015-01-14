/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.servlets.workspace;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.DirectoryWalker;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles the creation of the file list for a users workspace. See Bugzilla 453114.
 * 
 * @author Anthony Hunter
 *
 */
public class FileListDirectoryWalker extends DirectoryWalker<File> {

	/**
	 * The root of a users workspace
	 */
	private File workspaceRoot;

	/** 
	 * The path of the workspace root used to create the path for the file.
	 */
	private String workspacePath;

	/** 
	 * The length of the absolute path of the workspace file. 
	 */
	private int workspaceRootSuffixLength;

	private String filter;

	private IFileStore workspaceHome;

	public FileListDirectoryWalker(WorkspaceInfo workspaceInfo) {
		this(workspaceInfo, null);
	}

	public FileListDirectoryWalker(WorkspaceInfo workspaceInfo, String filterPath) {
		init(workspaceInfo, filterPath);
	}

	private void init(WorkspaceInfo workspaceInfo, String filterPath) {
		String workspaceId = workspaceInfo.getUniqueId();
		filter = filterPath;
		IFileStore userHome = OrionConfiguration.getMetaStore().getUserHome(workspaceInfo.getUserId());
		workspaceHome = userHome.getChild(workspaceId.substring(workspaceId.indexOf('-') + 1));
		try {
			workspaceRoot = workspaceHome.toLocalFile(EFS.NONE, null);
		} catch (CoreException e) {
			//should never happen
			throw new RuntimeException(e);
		}
		workspacePath = "/file/" + workspaceId + "/";
		workspaceRootSuffixLength = workspaceRoot.getAbsolutePath().length() + 1;
	}

	@Override
	protected void handleFile(final File file, final int depth, final Collection<File> results) throws IOException {
		results.add(file);
	}

	public JSONObject getFileList() {
		List<File> files = new ArrayList<File>();
		try {
			if (filter != null) {
				IFileStore projHome = workspaceHome.getChild(filter);
				try {
					File projRoot = projHome.toLocalFile(EFS.NONE, null);
					walk(projRoot, files);
				} catch (CoreException e) {
					//should never happen
					throw new RuntimeException(e);
				}
			} else {
				walk(workspaceRoot, files);
			}
		} catch (IOException e) {
			//should never happen
			throw new RuntimeException(e);
		}

		JSONObject json = new JSONObject();
		try {
			JSONArray jsonArray = new JSONArray();
			for (File file : files) {
				jsonArray.put(toJSON(file, workspaceRootSuffixLength));
			}
			json.put("FileList", jsonArray);
			json.put("Timestamp", System.currentTimeMillis());
		} catch (JSONException e) {
			//should never happen
			throw new RuntimeException(e);
		}
		return json;
	}

	private JSONObject toJSON(File file, int workspaceRootSuffixLength) throws JSONException {
		JSONObject json = new JSONObject();
		String filePath = file.getAbsolutePath().substring(workspaceRootSuffixLength);
		String sha = checkSum(file.getAbsolutePath());
		json.put(ProtocolConstants.KEY_PATH, workspacePath + filePath);
		json.put("FilePath", filePath);
		json.put(ProtocolConstants.KEY_LENGTH, Long.toString(file.length()));
		json.put(ProtocolConstants.KEY_LAST_MODIFIED, Long.toString(file.lastModified()));
		json.put("SHA", sha);
		return json;
	}

	String checkSum(String path) {
		FileInputStream stream = null;
		try {
			stream = new FileInputStream(path);
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = stream.read(buffer)) > 0) {
				digest.update(buffer, 0, bytesRead);
			}
			byte[] hashedBytes = digest.digest();
			return convertByteArrayToHexString(hashedBytes);
		} catch (Exception e) {
		} finally {
			if (stream != null)
				try {
					stream.close();
				} catch (IOException e) {
				}
		}
		return "";
	}

	private static String convertByteArrayToHexString(byte[] arrayBytes) {
		StringBuffer stringBuffer = new StringBuffer();
		for (int i = 0; i < arrayBytes.length; i++) {
			stringBuffer.append(Integer.toString((arrayBytes[i] & 0xff) + 0x100, 16).substring(1));
		}
		return stringBuffer.toString();
	}

}