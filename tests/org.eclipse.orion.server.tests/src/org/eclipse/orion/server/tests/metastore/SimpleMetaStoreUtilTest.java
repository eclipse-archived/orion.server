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
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStoreUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SimpleMetaStoreUtilTest {

	public static void deleteFile(File parentFile) {
		if (parentFile.isDirectory()) {
			File[] allFiles = parentFile.listFiles();
			if (allFiles.length == 0) {
				parentFile.delete();
			} else {
				for (File file : allFiles) {
					deleteFile(file);
				}
				parentFile.delete();
			}
		} else {
			parentFile.delete();
		}
	}

	private File tempDir = null;

	private File getTempDir() {
		if (tempDir == null) {
			tempDir = new File(FileSystemHelper.getRandomLocation(FileSystemHelper.getTempDir()).toOSString());
			tempDir.mkdir();
		}
		return tempDir;
	}

	@Test
	public void testCreateMetaFile() throws JSONException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		jsonObject.put("boolean", true);
		jsonObject.put("String", "String");
		JSONArray jsonArray = new JSONArray();
		jsonArray.put("one");
		jsonArray.put("two");
		jsonArray.put("three");
		jsonObject.put("Array", jsonArray);
		File parent = getTempDir();
		String name = "test";
		String createdName = "test.json";
		// the file is not there at the start
		assertFalse(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// create the file.
		assertTrue(SimpleMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the file is now there.
		assertTrue(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// check the filesystem, the file is really there
		List<String> files = Arrays.asList(parent.list());
		assertTrue(files.contains(createdName));
		// delete the file.
		assertTrue(SimpleMetaStoreUtil.deleteMetaFile(parent, name));
		// the file is not there now.
		assertFalse(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// check the filesystem, the file is really gone now
		files = Arrays.asList(parent.list());
		assertFalse(files.contains(createdName));
	}

	@Test
	public void testDeleteMetaFile() throws JSONException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		File parent = getTempDir();
		String name = "test";
		String createdName = "test.json";
		// the file is not there at the start
		assertFalse(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// check the filesystem, the file is really not there
		List<String> files = Arrays.asList(parent.list());
		assertFalse(files.contains(createdName));
		// create the file.
		assertTrue(SimpleMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the file is now there.
		assertTrue(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// check the filesystem, the file is really there
		files = Arrays.asList(parent.list());
		assertTrue(files.contains(createdName));
		// delete the file.
		assertTrue(SimpleMetaStoreUtil.deleteMetaFile(parent, name));
		// the file is not there now.
		assertFalse(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// check the filesystem, the file is really gone now
		files = Arrays.asList(parent.list());
		assertFalse(files.contains(createdName));
	}

	@Test
	public void testDeleteMetaFolder() {
		File parent = getTempDir();
		String name = "test";
		// the folder is not there at the start
		assertFalse(SimpleMetaStoreUtil.isMetaFolder(parent, name));
		// check the filesystem, the folder is really not there
		List<String> files = Arrays.asList(parent.list());
		assertFalse(files.contains(name));
		// create the folder.
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(parent, name));
		// the folder is now there.
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(parent, name));
		// check the filesystem, the folder is really there
		files = Arrays.asList(parent.list());
		assertTrue(files.contains(name));
		// delete the folder.
		assertTrue(SimpleMetaStoreUtil.deleteMetaFolder(parent, name, true));
		// the folder is not there now.
		assertFalse(SimpleMetaStoreUtil.isMetaFolder(parent, name));
		// check the filesystem, the folder is really gone now
		files = Arrays.asList(parent.list());
		assertFalse(files.contains(name));
	}

	@Test(expected = RuntimeException.class)
	public void testCreateMetaFileWithBadName() throws JSONException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		File parent = getTempDir();
		String name = "this//is//bad";
		// try to create the file
		assertTrue(SimpleMetaStoreUtil.createMetaFile(parent, name, jsonObject));
	}

	@Test
	public void testCreateMetaFolder() {
		File parent = getTempDir();
		String name = "test";
		// the folder is not there at the start
		assertFalse(SimpleMetaStoreUtil.isMetaFolder(parent, name));
		// create the folder.
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(parent, name));
		// the folder is now there.
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(parent, name));
		// check the filesystem, the folder is really there
		List<String> files = Arrays.asList(parent.list());
		assertTrue(files.contains(name));
		// delete the folder.
		assertTrue(SimpleMetaStoreUtil.deleteMetaFolder(parent, name, true));
		// the folder is not there now.
		assertFalse(SimpleMetaStoreUtil.isMetaFolder(parent, name));
		// check the filesystem, the folder is really gone now
		files = Arrays.asList(parent.list());
		assertFalse(files.contains(name));
	}

	@Test
	public void testEncodedWorkspaceId() {
		String userName = "anthony";
		String workspaceName = "Workspace One";
		String encoded = SimpleMetaStoreUtil.encodeWorkspaceId(userName, workspaceName);
		assertTrue(userName.equals(SimpleMetaStoreUtil.decodeUserIdFromWorkspaceId(encoded)));
		assertTrue(workspaceName.replaceAll(" ", "").equals(SimpleMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(encoded)));
	}

	@Test
	public void testListMetaFilesAndFolders() throws JSONException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("version", 1);
		File parent = getTempDir();

		// create the first folder
		String name1 = "name1";
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(parent, name1));
		// create the first file
		assertTrue(SimpleMetaStoreUtil.createMetaFile(parent, name1, jsonObject));

		// create the second folder
		String name2 = "name2";
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(parent, name2));
		// create the second file
		assertTrue(SimpleMetaStoreUtil.createMetaFile(parent, name2, jsonObject));

		// get the list of files
		List<String> savedFiles = SimpleMetaStoreUtil.listMetaFiles(parent);
		assertEquals(savedFiles.size(), 2);
		assertTrue(savedFiles.contains(name1));
		assertTrue(savedFiles.contains(name2));

		// get the list of folders
		List<String> savedFolders = SimpleMetaStoreUtil.listMetaFiles(parent);
		assertEquals(savedFolders.size(), 2);
		assertTrue(savedFolders.contains(name1));
		assertTrue(savedFolders.contains(name2));

		// delete the first file
		assertTrue(SimpleMetaStoreUtil.deleteMetaFile(parent, name1));
		// delete the first folder
		assertTrue(SimpleMetaStoreUtil.deleteMetaFolder(parent, name1, true));

		// get the list of files
		savedFiles = SimpleMetaStoreUtil.listMetaFiles(parent);
		assertEquals(savedFiles.size(), 1);
		assertFalse(savedFiles.contains(name1));
		assertTrue(savedFiles.contains(name2));

		// get the list of folders
		savedFolders = SimpleMetaStoreUtil.listMetaFiles(parent);
		assertEquals(savedFolders.size(), 1);
		assertFalse(savedFolders.contains(name1));
		assertTrue(savedFolders.contains(name2));

		// delete the first file
		assertTrue(SimpleMetaStoreUtil.deleteMetaFile(parent, name2));
		// delete the first folder
		assertTrue(SimpleMetaStoreUtil.deleteMetaFolder(parent, name2, true));
	}

	@Test
	public void testReadMetaFile() throws JSONException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		jsonObject.put("boolean", true);
		jsonObject.put("String", "String");
		JSONArray jsonArray = new JSONArray();
		jsonArray.put("one");
		jsonArray.put("two");
		jsonArray.put("three");
		jsonObject.put("Array", jsonArray);
		File parent = getTempDir();
		String name = "test";
		String createdName = "test.json";
		// the file is not there at the start
		assertFalse(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// create the file.
		assertTrue(SimpleMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the file is now there.
		assertTrue(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// check the filesystem, the file is really there
		List<String> files = Arrays.asList(parent.list());
		assertTrue(files.contains(createdName));
		// retrieve the JSON
		JSONObject jsonObjectNew = SimpleMetaStoreUtil.readMetaFile(parent, name);
		assertNotNull(jsonObjectNew);
		assertTrue(jsonObjectNew.getBoolean("boolean"));
		assertEquals(jsonObjectNew.getInt("int"), 1);
		assertEquals(jsonObjectNew.getJSONArray("Array").length(), 3);
		assertEquals(jsonObjectNew.getString("String"), "String");
		// delete the file.
		assertTrue(SimpleMetaStoreUtil.deleteMetaFile(parent, name));
	}

	@Test
	public void testReadMetaFolder() {
		File parent = getTempDir();
		String name = "test";
		// the folder is not there at the start
		assertFalse(SimpleMetaStoreUtil.isMetaFolder(parent, name));
		// create the folder.
		assertTrue(SimpleMetaStoreUtil.createMetaFolder(parent, name));
		// the folder is now there.
		assertTrue(SimpleMetaStoreUtil.isMetaFolder(parent, name));
		// check the filesystem, the folder is really there
		List<String> files = Arrays.asList(parent.list());
		assertTrue(files.contains(name));
		// retrieve the folder
		File newFolder = SimpleMetaStoreUtil.readMetaFolder(parent, name);
		assertNotNull(newFolder);
		assertTrue(newFolder.isDirectory());
		assertTrue(newFolder.exists());
		// delete the folder.
		assertTrue(SimpleMetaStoreUtil.deleteMetaFolder(parent, name, true));
	}

	@Test
	public void testUpdateMetaFile() throws JSONException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		jsonObject.put("boolean", true);
		jsonObject.put("String", "String");
		JSONArray jsonArray = new JSONArray();
		jsonArray.put("one");
		jsonArray.put("two");
		jsonArray.put("three");
		jsonObject.put("Array", jsonArray);
		File parent = getTempDir();
		String name = "test";
		String createdName = "test.json";
		// the file is not there at the start
		assertFalse(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// create the file.
		assertTrue(SimpleMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the file is now there.
		assertTrue(SimpleMetaStoreUtil.isMetaFile(parent, name));
		// check the filesystem, the file is really there
		List<String> files = Arrays.asList(parent.list());
		assertTrue(files.contains(createdName));
		// update the JSON
		jsonObject.put("int", 100);
		jsonObject.put("boolean", false);
		jsonObject.remove("string");
		// update with new JSON.
		assertTrue(SimpleMetaStoreUtil.updateMetaFile(parent, name, jsonObject));
		// retrieve the JSON
		JSONObject jsonObjectNew = SimpleMetaStoreUtil.readMetaFile(parent, name);
		assertNotNull(jsonObjectNew);
		assertFalse(jsonObjectNew.getBoolean("boolean"));
		assertEquals(jsonObjectNew.getInt("int"), 100);
		assertTrue(jsonObjectNew.has("String"));
		// delete the file
		assertTrue(SimpleMetaStoreUtil.deleteMetaFile(parent, name));
	}

	@Test
	public void testZDeleteTempDir() {
		// Very last test, delete the temporary folder
		File parent = getTempDir();
		if (parent.exists()) {
			// delete the root
			deleteFile(parent);
		}
		if (parent.exists()) {
			fail("Could not delete the temporary folder, something is wrong.");
		}
	}
}
