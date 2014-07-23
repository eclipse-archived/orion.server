package org.eclipse.orion.server.git.objects;

import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;

@ResourceDescription(type = Stash.TYPE)
public class Stash extends GitObject {

	public static final String RESOURCE = "stash";
	public static final String TYPE = "Stash";

	Stash(URI cloneLocation, Repository db) {
		super(cloneLocation, db);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		// TODO Auto-generated method stub
		return null;
	}
}
