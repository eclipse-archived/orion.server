package org.eclipse.orion.server.tests.filesystem.git.performance;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.tests.harness.FileSystemHelper;
import org.eclipse.orion.internal.server.filesystem.git.Utils;
import org.eclipse.orion.server.filesystem.git.GitFileStore;
import org.eclipse.orion.server.filesystem.git.GitFileSystem;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.junit.*;

public class PerformanceTest {
	private IPath repositoryPath;
	GitFileSystem fs = new GitFileSystem();

	@Before
	public void before() {
		repositoryPath = getRandomLocation();
	}

	@After
	public void removeSharedRepo() {
		FileSystemHelper.clear(repositoryPath.toFile());
	}

	@Test
	public void cloneInitDelete() throws CoreException {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this
				.getClass().getName() + '#' + "cloneInitDelete()");
		try {
			String s = Utils.encodeLocalPath(repositoryPath.toString());
			URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s
					+ "?/");

			for (int i = 0; i < 10; i++) {
				GitFileStore root = (GitFileStore) fs.getStore(uri);
				FileSystemHelper.clear(root.getLocalFile());
				removeSharedRepo();
				perfMeter.start();
				root.mkdir(EFS.NONE, null);

				assertTrue(root.isRoot());
				FileSystemHelper.clear(root.getLocalFile());

				root.mkdir(EFS.NONE, null);
				root.delete(EFS.NONE, null);

				perfMeter.stop();
			}
			perfMeter.commit();
			perf.assertPerformance(perfMeter);
		} finally {
			perfMeter.dispose();
		}
	}

	@Test
	public void createCopyDelete() throws IOException, CoreException {
		Performance perf = Performance.getDefault();
		PerformanceMeter perfMeter = perf.createPerformanceMeter(this
				.getClass().getName() + '#' + "createCopyDelete()");
		try {
			String s = Utils.encodeLocalPath(repositoryPath.toString());
			URI uri = URI.create(GitFileSystem.SCHEME_GIT + "://test/" + s
					+ "?/");

			for (int i = 0; i < 10; i++) {
				GitFileStore root = (GitFileStore) fs.getStore(uri);
				FileSystemHelper.clear(root.getLocalFile());
				removeSharedRepo();
				perfMeter.start();
				root.mkdir(EFS.NONE, null);

				IFileInfo info = root.fetchInfo();
				assertTrue("1.1", info.exists());
				assertTrue("1.2", info.isDirectory());

				IFileStore folder = root.getChild("folder");
				folder.mkdir(EFS.NONE, null);

				IFileStore file1 = folder.getChild("file1.txt");
				OutputStream out = file1.openOutputStream(EFS.NONE, null);
				out.write(1);
				out.close();

				IFileStore file2 = folder.getChild("file2.txt");
				out = file1.openOutputStream(EFS.NONE, null);
				out.write(2);
				out.close();

				IFileStore file3 = folder.getChild("file1.txt");
				out = file1.openOutputStream(EFS.NONE, null);
				out.write(1);
				out.close();

				IFileInfo[] childInfos = folder.childInfos(EFS.NONE, null);
				String[] childNames = folder.childNames(EFS.NONE, null);
				IFileStore[] childStores = folder.childStores(EFS.NONE, null);

				for (IFileStore child : childStores) {
					child.delete(EFS.NONE, null);
				}
				perfMeter.stop();
			}
			perfMeter.commit();
			perf.assertPerformance(perfMeter);
		} finally {
			perfMeter.dispose();
		}
	}

	private IPath getRandomLocation() {
		return FileSystemHelper
				.getRandomLocation(FileSystemHelper.getTempDir());
	}
}
