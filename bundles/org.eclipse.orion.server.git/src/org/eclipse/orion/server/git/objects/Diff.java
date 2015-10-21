/*******************************************************************************
 * Copyright (c) 2011, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.objects;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.eclipse.orion.server.git.servlets.GitUtils;
import org.eclipse.osgi.util.NLS;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Diff.TYPE)
public class Diff extends GitObject {

	public static final String RESOURCE = "diff"; //$NON-NLS-1$
	public static final String TYPE = "Diff"; //$NON-NLS-1$

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(ProtocolConstants.KEY_LOCATION), // super
				new Property(GitConstants.KEY_CLONE), // super
				new Property(GitConstants.KEY_COMMIT_OLD), //
				new Property(GitConstants.KEY_COMMIT_NEW), //
				new Property(GitConstants.KEY_COMMIT_BASE) };
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	private URI baseLocation;

	public Diff(URI baseLocation, Repository db) throws URISyntaxException, CoreException {
		super(BaseToCloneConverter.getCloneLocation(baseLocation, BaseToCloneConverter.DIFF), db);
		this.baseLocation = baseLocation;
	}

	@Override
	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException, CoreException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return baseLocation;
	}

	@PropertyDescription(name = GitConstants.KEY_COMMIT_OLD)
	private URI getOldLocation() throws URISyntaxException {
		IPath path = new Path(baseLocation.getPath()).removeFirstSegments(2);
		return getOldLocation(baseLocation, path);
	}

	private URI getOldLocation(URI location, IPath path) throws URISyntaxException {
		String scope = path.segment(0);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				throw new IllegalArgumentException(NLS.bind("Illegal scope format, expected {old}..{new}, was {0}", scope));
			}
			IPath p = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(commits[0]).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), "parts=body", null); //$NON-NLS-1$
		} else if (scope.equals(GitConstants.KEY_DIFF_CACHED)) {
			IPath p = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(Constants.HEAD).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), "parts=body", null); //$NON-NLS-1$
		} else if (scope.equals(GitConstants.KEY_DIFF_DEFAULT)) {
			IPath p = new Path(GitServlet.GIT_URI + '/' + Index.RESOURCE).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), null, null);
		} else {
			IPath p = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(scope).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), "parts=body", null); //$NON-NLS-1$
		}
	}

	@PropertyDescription(name = GitConstants.KEY_COMMIT_NEW)
	private URI getNewLocation() throws URISyntaxException {
		IPath path = new Path(baseLocation.getPath()).removeFirstSegments(2);
		return getNewLocation(baseLocation, path);
	}

	private URI getNewLocation(URI location, IPath path) throws URISyntaxException {
		String scope = path.segment(0);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				throw new IllegalArgumentException(NLS.bind("Illegal scope format, expected {old}..{new}, was {0}", scope));
			}
			IPath p = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(commits[1]).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), "parts=body", null); //$NON-NLS-1$
		} else if (scope.equals(GitConstants.KEY_DIFF_CACHED)) {
			IPath p = new Path(GitServlet.GIT_URI + '/' + Index.RESOURCE).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), null, null);
		} else {
			/* including scope.equals(GitConstants.KEY_DIFF_DEFAULT */
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), path.removeFirstSegments(1).makeAbsolute()
					.toString(), null, null);
		}
	}

	@PropertyDescription(name = GitConstants.KEY_COMMIT_BASE)
	private URI getBaseLocation() throws URISyntaxException, IOException {
		IPath path = new Path(baseLocation.getPath()).removeFirstSegments(2);
		return getBaseLocation(baseLocation, db, path);
	}

	private URI getBaseLocation(URI location, Repository db, IPath path) throws URISyntaxException, IOException {
		String scope = path.segment(0);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				throw new IllegalArgumentException(NLS.bind("Illegal scope format, expected {old}..{new}, was {0}", scope));
			}
			ThreeWayMerger merger = new ResolveMerger(db) {
//				@Override
//				protected boolean mergeImpl() throws IOException {
//					// do nothing
//					return false;
//				}
			};
			// use #merge to set sourceObjects
			String tip0 = GitUtils.decode(commits[0]);
			String tip1 = GitUtils.decode(commits[1]);
			merger.merge(new ObjectId[] { db.resolve(tip0), db.resolve(tip1) });

			IPath p = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(merger.getBaseCommitId().getName()).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), "parts=body", null); //$NON-NLS-1$
		} else if (scope.equals(GitConstants.KEY_DIFF_CACHED)) {
			// HEAD is the base
			IPath p = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(Constants.HEAD).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), "parts=body", null); //$NON-NLS-1$
		} else {
			// index is the base
			IPath p = new Path(GitServlet.GIT_URI + '/' + Index.RESOURCE).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), null, null);
		}
	}
}
