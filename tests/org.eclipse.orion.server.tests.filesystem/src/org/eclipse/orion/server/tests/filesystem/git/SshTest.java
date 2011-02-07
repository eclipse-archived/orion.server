/*******************************************************************************
 *  Copyright (c) 2011 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.filesystem.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.crypto.spec.PBEKeySpec;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.equinox.security.storage.*;
import org.eclipse.equinox.security.storage.provider.IProviderHints;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.eclipse.orion.server.tests.filesystem.Activator;
import org.eclipse.osgi.service.datalocation.Location;
import org.junit.*;
import org.osgi.framework.*;

public class SshTest {

	// TODO: required setup:
	// * public key >> authorized_keys in Cygwin
	// * ensure one of the sshconfig bundles is started

	public static final String CYGWIN_BIN;
	public static final String CYGWIN_USER_HOME;
	public static final String LOGIN;
	public static final String PASSWORD;
	public static final String PUBLIC_KEY;
	public static final String KNOWN_HOSTS;
	public static final String PRIVATE_KEY;
	public static final String PASSPHRASE;

	static {
		// parse file with properties for the test
		String propertiesFile = System.getProperty("orion.sshtest.properties");
		// if (propertiesFile == null) return;
		File file = new File(propertiesFile);
		if (file.isDirectory()) file = new File(file, "sshtest.properties");
		Map<String, String> properties = new HashMap<String, String>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			try {
				for (String line; (line = reader.readLine()) != null; ) {
					if (line.startsWith("#"))
						continue;
					int sep = line.indexOf("=");
					String property = line.substring(0, sep).trim();
					String value = line.substring(sep + 1).trim();
					properties.put(property, value);
				}
			} finally {
				reader.close();
			}
		} catch (Exception e) {
			System.err.println("Could not read repository properties file: " + file.getAbsolutePath());
		}
		// initialize constants
		CYGWIN_BIN = properties.get("cygwinBin");
		CYGWIN_USER_HOME = properties.get("cygwinUserHome");
		LOGIN = properties.get("login");
		PASSWORD = properties.get("password");
		PASSPHRASE = properties.get("passphrase");
		KNOWN_HOSTS = properties.get("knownHosts");
		String content = null;
		try {
			 content = loadFileContents(properties.get("privateKeyPath"));
		} catch (Exception e) {
			System.err.println("Could not read repository properties file: " + file.getAbsolutePath());
		}
		PRIVATE_KEY = content;
		try {
			 content = loadFileContents(properties.get("publicKeyPath"));
		} catch (Exception e) {
			System.err.println("Could not read repository properties file: " + file.getAbsolutePath());
		}
		PUBLIC_KEY = content;

	}

	private static String loadFileContents(String path) throws IOException {
		File file = new File(path);
		InputStream is = new FileInputStream(file);
		return toString(is);
	}

	GitFileSystem fs = new GitFileSystem();
	GitFileStore store;

	@Test
	public void cloneUsernameAndPasswordProvided() throws URISyntaxException {
		ensureKeysAreNotUsed();
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append("ssh://");
		sb.append(LOGIN);
		sb.append(":");
		sb.append(PASSWORD);
		sb.append("@");
		sb.append("localhost/git/test.git");
		sb.append("?/");
		URI uri = new URI(sb.toString());
		store = (GitFileStore) fs.getStore(uri);
		try {
			store.mkdir(EFS.NONE, null);
		} catch (CoreException e) {
			fail("1.99");
		}
		IFileInfo info = store.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());
	}

	@Test(expected=CoreException.class)
	public void cloneBadPasswordInUri() throws URISyntaxException, CoreException {
		ensureKeysAreNotUsed();
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append("ssh://");
		sb.append(LOGIN);
		sb.append(":");
		sb.append("badpassword");
		sb.append("@");
		sb.append("localhost/git/test.git");
		sb.append("?/");
		URI uri = new URI(sb.toString());
		store = (GitFileStore) fs.getStore(uri);
		store.mkdir(EFS.NONE, null);
	}

	@Test
	public void cloneKeysNoPassphrase() throws URISyntaxException {
		ensureKeysAreUsed();
		StringBuffer sb = new StringBuffer();
		sb.append(GitFileSystem.SCHEME_GIT);
		sb.append("://test/");
		sb.append("ssh://");
		sb.append("localhost/git/test.git");
		sb.append("?/");
		URI uri = new URI(sb.toString());
		store = (GitFileStore) fs.getStore(uri);
		try {
			store.mkdir(EFS.NONE, null);
		} catch (CoreException e) {
			fail("1.99");
		}
		IFileInfo info = store.fetchInfo();
		assertTrue("1.1", info.exists());
		assertTrue("1.2", info.isDirectory());
		ensureKeysAreNotUsed();
	}

	@After
	public void removeClone() throws IOException {
		store.getLocalRepo().close();
		FileSystemHelper.clear(store.getLocalFile());
		assertFalse(store.getLocalFile().exists());
	}

	@BeforeClass
	public static void addTestUserSecrets() throws StorageException, IOException {
		ISecurePreferences prefs = initSecurePreferences();
		ISecurePreferences sshConfig = prefs.node("org.eclipse.orion.server.sshconfig.securestorage");
		ISecurePreferences test = sshConfig.node("test");
		test.put("knownHosts", KNOWN_HOSTS, false);
		String encodedUri = URLEncoder.encode("ssh://localhost/git/test.git", "UTF-8");
		ISecurePreferences uri1 = test.node(encodedUri);
		uri1.put("username", LOGIN, false);
		uri1.put("password", PASSWORD, true);
		// TODO: name for the key set
		ISecurePreferences keys = uri1.node("keys");
		ISecurePreferences id_rsa = keys.node("id_rsa");
		id_rsa.put("privateKey", PRIVATE_KEY, true);
		id_rsa.put("passphrase", PASSPHRASE, true);
		id_rsa.put("publicKey", PUBLIC_KEY, false);
		prefs.flush();
	}

	@AfterClass
	public static void clearTestUserSecrets() throws IOException {
		ISecurePreferences prefs = initSecurePreferences();
		if (prefs.node("org.eclipse.orion.server.sshconfig.securestorage").nodeExists("test")) {
			prefs.node("org.eclipse.orion.server.sshconfig.securestorage").node("test").removeNode();
		}
	}

	@BeforeClass
	public static void ensureSshdStarted() throws IOException, InterruptedException {
		File binDir = new File(CYGWIN_BIN);
		String cmd = "bash --login -i -c \"net start sshd\"";
		Process process = Runtime.getRuntime().exec(binDir + "/" + cmd, null, binDir);
		String output = toString(process.getInputStream());
		assertEquals("The CYGWIN sshd service is starting.\r\nThe CYGWIN sshd service was started successfully.\r\n\r\n", output);
		assertEquals(0, process.waitFor());
	}

	@AfterClass
	public static void ensureSshdStopped() throws IOException, InterruptedException {
		File binDir = new File(CYGWIN_BIN);
		String cmd = "bash --login -i -c \"net stop sshd\"";
		Process process = Runtime.getRuntime().exec(binDir + "/" + cmd, null, binDir);
		String output = toString(process.getInputStream());
		assertEquals("The CYGWIN sshd service is stopping.\r\nThe CYGWIN sshd service was stopped successfully.\r\n\r\n", output);
		assertEquals(0, process.waitFor());
	}

	private static String toString(InputStream is) throws IOException {
		if (is != null) {
			Writer writer = new StringWriter();
			char[] buffer = new char[1024];
			try {
				Reader reader = new BufferedReader(new InputStreamReader(is,
						"UTF-8"));
				int n;
				while ((n = reader.read(buffer)) != -1) {
					writer.write(buffer, 0, n);
				}
			} finally {
				is.close();
			}
			return writer.toString();
		} else {
			return "";
		}
	}

	private void ensureKeysAreUsed() {
		File sshDir = new File(CYGWIN_USER_HOME, ".ssh");
		if (!sshDir.exists()) {
			sshDir.mkdir();
			// TODO: copy user's public key from PUBLIC_KEY
		}

		File authorizedKeysFile = new File(sshDir, "authorized_keys2");
		if (authorizedKeysFile.exists())
			return; // nothing to do, assuming the right key is there

		File authorizedKeysBakFile = new File(sshDir, "authorized_keys2.bak");
		if (authorizedKeysBakFile.exists()) {
			copy(authorizedKeysBakFile, authorizedKeysFile);
		}
		assertTrue(authorizedKeysFile.exists());
	}

	private void ensureKeysAreNotUsed() {
		File sshDir = new File(CYGWIN_USER_HOME, ".ssh");
		if (!sshDir.exists()) {
			return; // no .ssh dir, no keys
		}

		File authorizedKeysFile = new File(sshDir, "authorized_keys2");
		if (!authorizedKeysFile.exists())
			return; // nothing to do

		File authorizedKeysBakFile = new File(sshDir, "authorized_keys2.bak");
		copy(authorizedKeysFile, authorizedKeysBakFile);

		authorizedKeysFile.delete();
		assertFalse(authorizedKeysFile.exists());
	}

	private void copy(File from, File to) {
		FileReader in = null;
		FileWriter out = null;
		try {
			in = new FileReader(from);
			out = new FileWriter(to);
			int c;
			while ((c = in.read()) != -1)
				out.write(c);
			in.close();
			out.close();
		} catch (IOException e) {
			fail(e.getMessage());
		} finally {
			try {
				if (in != null)
					in.close();
				if (out != null)
					out.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	private static ISecurePreferences initSecurePreferences() throws IOException {
		//try to create our own secure storage under the platform instance location
		URL location = getStorageLocation();
		if (location != null) {
			Map<String, Object> options = new HashMap<String, Object>();
			options.put(IProviderHints.PROMPT_USER, Boolean.FALSE);
			String password = System.getProperty("orion.storage.password", "");
			options.put(IProviderHints.DEFAULT_PASSWORD, new PBEKeySpec(password.toCharArray()));
				return SecurePreferencesFactory.open(location, options);
		}
		//fall back to default secure storage location if we failed to create our own
		return SecurePreferencesFactory.getDefault().node("org.eclipse.orion.server");
	}

	/**
	 * Returns the location for user data to be stored.
	 * @throws IOException
	 */
	private static URL getStorageLocation() throws IOException {
		BundleContext context = Activator.getContext();
		Collection<ServiceReference<Location>> refs;
		try {
			refs = context.getServiceReferences(Location.class, Location.INSTANCE_FILTER);
		} catch (InvalidSyntaxException e) {
			// we know the instance location filter syntax is valid
			throw new RuntimeException(e);
		}
		if (refs.isEmpty())
			return null;
		ServiceReference<Location> ref = refs.iterator().next();
		Location location = context.getService(ref);
		try {
			if (location != null)
				return location.getDataArea("org.eclipse.orion.server.sshconfig.securestorage" + "/user_store"); //$NON-NLS-1$
		} finally {
			context.ungetService(ref);
		}
		//return null if we are unable to determine instance location.
		return null;
	}


}
