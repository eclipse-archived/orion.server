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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.orion.internal.server.core.metastore.SimpleLinuxMetaStoreUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SimpleLinuxMetaStoreUtilTest {

	public final static String TEST_META_STORE = "file:/tmp/org.eclipse.orion.server.tests.metastore";

	public static URI createTestMetaStoreFolder() throws URISyntaxException {
		URI parent = new URI(TEST_META_STORE);
		File parentFile = new File(parent);
		if (parentFile.exists()) {
			// file must exist from a failed JUnit test, delete
			deleteFile(parentFile);
		}
		if (!parentFile.mkdir()) {
			throw new RuntimeException("Could not create JUnit Temp Folder, something is wrong:" + parent.toString());
		}
		return parent;
	}

	private static void deleteFile(File parentFile) {
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

	@Test
	public void testCreateMetaFile() throws JSONException, URISyntaxException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		jsonObject.put("boolean", true);
		jsonObject.put("String", "String");
		JSONArray jsonArray = new JSONArray();
		jsonArray.put("one");
		jsonArray.put("two");
		jsonArray.put("three");
		jsonObject.put("Array", jsonArray);
		URI parent = createTestMetaStoreFolder();
		String name = "test";
		// the folder is not there at the start
		assertFalse("MetaFile already exists", SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		// create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the folder is now there
		assertTrue(SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaFile(parent, name));
	}

	@Test
	public void testCreateMetaFileURI() throws URISyntaxException {
		URI parent = createTestMetaStoreFolder();
		URI shouldGet = new URI(parent.toString() + "/name.json");
		URI result = SimpleLinuxMetaStoreUtil.retrieveMetaFileURI(parent, "name");
		assertEquals(shouldGet, result);
	}

	@Test(expected = RuntimeException.class)
	public void testCreateMetaFileWithBadName() throws JSONException, URISyntaxException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		URI parent = createTestMetaStoreFolder();
		String name = "this is bad";
		// try to create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(parent, name, jsonObject));
	}

	@Test
	public void testCreateMetaFolderURI() throws URISyntaxException {
		URI parent = createTestMetaStoreFolder();
		URI shouldGet = new URI(parent.toString() + "/name");
		URI result = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(parent, "name");
		assertEquals(shouldGet, result);
	}

	@Test
	public void testCreateMetaRoot() throws URISyntaxException, JSONException {
		URI parent = createTestMetaStoreFolder();
		// the root is not there at the start
		assertFalse(SimpleLinuxMetaStoreUtil.isMetaStoreRoot(parent));
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("Version", 1);
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaStoreRoot(parent, jsonObject));
		JSONObject jsonObjectNew = SimpleLinuxMetaStoreUtil.retrieveMetaStoreRoot(parent);
		assertNotNull(jsonObjectNew);
		assertEquals(jsonObjectNew.getInt("Version"), 1);
		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaStoreRoot(parent));
		// the root is not there again
		assertFalse(SimpleLinuxMetaStoreUtil.isMetaStoreRoot(parent));
	}

	@Test
	public void testDeleteTree() throws URISyntaxException {
		URI parent = createTestMetaStoreFolder();
		// the root is not there at the start
		assertFalse(SimpleLinuxMetaStoreUtil.isMetaStoreRoot(parent));
		JSONObject jsonObject = new JSONObject();
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaStoreRoot(parent, jsonObject));
		String folder = "folder";
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFolder(parent, folder));
		URI uri = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(parent, folder);
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(uri, "name1", jsonObject));
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(uri, "name2", jsonObject));
		// the meta store is not empty, delete fails
		SimpleLinuxMetaStoreUtil.deleteMetaStoreRoot(parent);
	}

	@Test
	public void testEncodedProjectId() {
		String userName = "anthony";
		String workspaceName = "workspace1";
		String projectName = "project1";
		String encoded = SimpleLinuxMetaStoreUtil.encodeProjectId(userName, workspaceName, projectName);
		assertTrue(userName.equals(SimpleLinuxMetaStoreUtil.decodeUserNameFromProjectId(encoded)));
		assertTrue(workspaceName.equals(SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromProjectId(encoded)));
		assertTrue(projectName.equals(SimpleLinuxMetaStoreUtil.decodeProjectNameFromProjectId(encoded)));
	}

	@Test
	public void testEncodedUserId() {
		String userName = "anthony";
		String encoded = SimpleLinuxMetaStoreUtil.encodeUserId(userName);
		assertTrue(userName.equals(SimpleLinuxMetaStoreUtil.decodeUserNameFromUserId(encoded)));
	}

	@Test
	public void testEncodedWorkspaceId() {
		String userName = "anthony";
		String workspaceName = "workspace1";
		String encoded = SimpleLinuxMetaStoreUtil.encodeWorkspaceId(userName, workspaceName);
		assertTrue(userName.equals(SimpleLinuxMetaStoreUtil.decodeUserNameFromWorkspaceId(encoded)));
		assertTrue(workspaceName.equals(SimpleLinuxMetaStoreUtil.decodeWorkspaceNameFromWorkspaceId(encoded)));
	}

	@Test
	public void testIsNameValid() {
		assertTrue(SimpleLinuxMetaStoreUtil.isNameValid("test"));
	}

	@Test
	public void testIsNameValidAsterixCharacterFail() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("asterisk-is-bad*"));
	}

	@Test
	public void testIsNameValidBackSlashFail() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("back-slash-is-bad\\"));
	}

	@Test
	public void testIsNameValidForwardSlashFail() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("forward-slash-is-bad/"));
	}

	@Test
	public void testIsNameValidSingleCharacterPass() {
		assertTrue(SimpleLinuxMetaStoreUtil.isNameValid("a"));
	}

	@Test
	public void testIsNameValidSpaceCharacterFail() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("space-is-bad "));
	}

	@Test
	public void testIsNameValidThirtyOneCharactersPass() {
		assertTrue(SimpleLinuxMetaStoreUtil.isNameValid("thirty-one-characters-just-fine"));
	}

	@Test
	public void testIsNameValidThirtyTwoCharactersFail() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("thirty-two-characters-is-toolong"));
	}

	@Test
	public void testIsNameValidUpperCaseCharactersFail() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("A"));
	}

	@Test
	public void testListMetaFiles() throws JSONException, URISyntaxException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		URI parent = createTestMetaStoreFolder();

		assertTrue(SimpleLinuxMetaStoreUtil.createMetaStoreRoot(parent, jsonObject));

		// create the folder
		String name1 = "name1";
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFolder(parent, name1));
		URI folder1 = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(parent, name1);

		// the meta file is not there at the start
		assertFalse(SimpleLinuxMetaStoreUtil.isMetaFile(folder1, name1));

		// create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(folder1, "name1", jsonObject));

		// the meta file is there now
		assertTrue(SimpleLinuxMetaStoreUtil.isMetaFile(folder1, name1));

		// create the folder
		String name2 = "name2";
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFolder(parent, name2));
		URI folder2 = SimpleLinuxMetaStoreUtil.retrieveMetaFolderURI(parent, name2);

		// the meta file is not there at the start
		assertFalse(SimpleLinuxMetaStoreUtil.isMetaFile(folder2, name2));

		// create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(folder2, name2, jsonObject));

		// the meta file is there now
		assertTrue(SimpleLinuxMetaStoreUtil.isMetaFile(folder2, name2));

		// get the list of files
		List<String> savedFiles = SimpleLinuxMetaStoreUtil.listMetaFiles(parent);
		assertEquals(savedFiles.size(), 2);
		assertTrue(savedFiles.contains(name1));
		assertTrue(savedFiles.contains(name2));

		// delete the files
		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaFile(folder1, name1));
		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaFolder(folder1));

		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaFile(folder2, name2));
		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaFolder(folder2));

		// delete the folder
		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaStoreRoot(parent));
	}

	@Test
	public void testRetrieveMetaFile() throws JSONException, URISyntaxException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		jsonObject.put("boolean", true);
		jsonObject.put("String", "String");
		JSONArray jsonArray = new JSONArray();
		jsonArray.put("one");
		jsonArray.put("two");
		jsonArray.put("three");
		jsonObject.put("Array", jsonArray);
		URI parent = createTestMetaStoreFolder();
		String name = "test";
		// the folder is not there at the start
		assertFalse("MetaFile already exists", SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		// create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the folder is now there
		assertTrue(SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		// retrieve the JSON
		JSONObject jsonObjectNew = SimpleLinuxMetaStoreUtil.retrieveMetaFile(parent, name);
		assertNotNull(jsonObjectNew);
		assertTrue(jsonObjectNew.getBoolean("boolean"));
		assertEquals(jsonObjectNew.getInt("int"), 1);
		assertEquals(jsonObjectNew.getJSONArray("Array").length(), 3);
		assertEquals(jsonObjectNew.getString("String"), "String");
		// delete the file
		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaFile(parent, name));
	}

	@Test
	public void testUpdateMetaFile() throws JSONException, URISyntaxException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		jsonObject.put("boolean", true);
		jsonObject.put("String", "String");
		JSONArray jsonArray = new JSONArray();
		jsonArray.put("one");
		jsonArray.put("two");
		jsonArray.put("three");
		jsonObject.put("Array", jsonArray);
		URI parent = createTestMetaStoreFolder();
		String name = "test";
		// the folder is not there at the start
		assertFalse("MetaFile already exists", SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		// create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the folder is now there
		assertTrue(SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		jsonObject.put("int", 100);
		jsonObject.put("boolean", false);
		jsonObject.remove("string");
		// update with new JSON.
		assertTrue(SimpleLinuxMetaStoreUtil.updateMetaFile(parent, name, jsonObject));
		// retrieve the JSON
		JSONObject jsonObjectNew = SimpleLinuxMetaStoreUtil.retrieveMetaFile(parent, name);
		assertNotNull(jsonObjectNew);
		assertFalse(jsonObjectNew.getBoolean("boolean"));
		assertEquals(jsonObjectNew.getInt("int"), 100);
		assertTrue(jsonObjectNew.has("String"));
		// delete the file
		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaFile(parent, name));
	}
}
