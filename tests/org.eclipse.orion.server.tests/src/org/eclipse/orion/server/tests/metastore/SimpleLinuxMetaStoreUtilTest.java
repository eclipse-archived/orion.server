package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.orion.internal.server.core.metastore.SimpleLinuxMetaStoreUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class SimpleLinuxMetaStoreUtilTest {

	@Before
	public void createMetaStoreTestFolder() throws URISyntaxException {
		URI parent = new URI("file:/tmp/org.eclipse.orion.server.tests.metastore");
		File parentFile = new File(parent);
		if (!parentFile.exists()) {
			if (!parentFile.mkdir()) {
				throw new IllegalArgumentException("Could not create JUnit Temp Folder, something is wrong:" + parent.toString());
			}
		}
	}

	@Test
	public void testAsterixIsNotValid() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("asterisk-is-bad*"));
	}

	@Test
	public void testBackSlashIsNotValid() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("back-slash-is-bad\\"));
	}

	@Test
	public void testCreateMetaFileURI() throws URISyntaxException {
		URI parent = new URI("file:/workspace/foo");
		URI shouldGet = new URI("file:/workspace/foo/name/name.json");
		URI result = SimpleLinuxMetaStoreUtil.createMetaFileURI(parent, "name");
		assertEquals(shouldGet, result);
	}

	@Test
	public void testCreateMetaRoot() throws URISyntaxException, JSONException {
		URI parent = new URI("file:/tmp/org.eclipse.orion.server.tests.metastore");
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

	@Test(expected = IllegalArgumentException.class)
	public void testCreateMetaFileWithBadName() throws JSONException, URISyntaxException {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("int", 1);
		URI parent = new URI("file:/tmp/org.eclipse.orion.server.tests.metastore");
		String name = "this is bad";
		// try to create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(parent, name, jsonObject));
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
		URI parent = new URI("file:/tmp/org.eclipse.orion.server.tests.metastore");
		String name = "test";
		// the directory is not there at the start
		assertFalse(SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		// create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the directory is now there
		assertTrue(SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		assertTrue(SimpleLinuxMetaStoreUtil.deleteMetaFile(parent, name));
	}

	@Test
	public void testForwardIsNotValid() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("forward-slash-is-bad/"));
	}

	@Test
	public void testIsNameValid() {
		assertTrue(SimpleLinuxMetaStoreUtil.isNameValid("test"));
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
		URI parent = new URI("file:/tmp/org.eclipse.orion.server.tests.metastore");
		String name = "test";
		// the directory is not there at the start
		assertFalse(SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		// create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the directory is now there
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
		URI parent = new URI("file:/tmp/org.eclipse.orion.server.tests.metastore");
		String name = "test";
		// the directory is not there at the start
		assertFalse(SimpleLinuxMetaStoreUtil.isMetaFile(parent, name));
		// create the file
		assertTrue(SimpleLinuxMetaStoreUtil.createMetaFile(parent, name, jsonObject));
		// the directory is now there
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

	@Test
	public void testSingleCharacterIsValid() {
		assertTrue(SimpleLinuxMetaStoreUtil.isNameValid("a"));
	}

	@Test
	public void testSpaceIsNotValid() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("space-is-bad "));
	}

	@Test
	public void testThirtyOneCharactersIsValid() {
		assertTrue(SimpleLinuxMetaStoreUtil.isNameValid("thirty-one-characters-just-fine"));
	}

	@Test
	public void testThirtyTwoCharactersIsNotValid() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("thirty-two-characters-is-toolong"));
	}

	@Test
	public void testUpperCaseCharactersIsNotValid() {
		assertFalse(SimpleLinuxMetaStoreUtil.isNameValid("A"));
	}
}
