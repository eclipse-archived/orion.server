package org.eclipse.orion.server.git.objects;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;

@ResourceDescription(type = Stash.TYPE)
public class Stash extends GitObject {

	public static final String RESOURCE = "stash"; //$NON-NLS-1$
	public static final String TYPE = "Stash"; //$NON-NLS-1$
	public static final Comparator<RevCommit> COMPARATOR = new Comparator<RevCommit>() {

		@Override
		public int compare(RevCommit o1, RevCommit o2) {
			return o1.getCommitTime() > o2.getCommitTime() ? 1 : o1.getId().equals(o2.getId()) ? 0 : -1;
		}
	};

	Stash(URI cloneLocation, Repository db) {
		super(cloneLocation, db);
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return null;
	}

}
