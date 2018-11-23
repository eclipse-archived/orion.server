package org.eclipse.orion.server.tests.servlets.files;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.internal.server.servlets.ChangeEvent;
import org.eclipse.orion.internal.server.servlets.IFileStoreModificationListener;
import org.eclipse.orion.internal.server.servlets.file.FilesystemModificationListenerManager;

public class TestFilesystemModificationListener implements IFileStoreModificationListener {

	final ArrayList<ChangeEvent> events = new ArrayList<ChangeEvent>();

	public TestFilesystemModificationListener() {
		FilesystemModificationListenerManager.getInstance().addListener(this);
	}

	@Override
	public void changed(ChangeEvent event) {
		events.add(event);
	}

	public void clear() {
		events.clear();
	}

	static void cleanup(TestFilesystemModificationListener l) {
		if (l == null) {
			return;
		}

		FilesystemModificationListenerManager.getInstance().removeListener(l);
	}

	public void assertListenerNotified(IFileStore modified, ChangeType type) {
		assertListenerNotified(null, modified, type);
	}

	public void assertListenerNotified(IFileStore initial, IFileStore modified, ChangeType type) {
		assertEquals(1, events.size());
		ChangeEvent event = events.get(0);

		assertEquals(type, event.getChangeType());
		assertEquals(initial, event.getInitialLocation());
		assertEquals(modified, event.getModifiedItem());
	}

}
