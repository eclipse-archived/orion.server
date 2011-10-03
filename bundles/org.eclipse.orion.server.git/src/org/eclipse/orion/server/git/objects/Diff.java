/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
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
import org.eclipse.core.runtime.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.server.git.BaseToCloneConverter;
import org.eclipse.orion.server.git.GitConstants;
import org.eclipse.orion.server.git.servlets.GitServlet;
import org.json.JSONException;
import org.json.JSONObject;

public class Diff extends GitObject {

	public static final String RESOURCE = "diff"; //$NON-NLS-1$
	public static final String TYPE = "Diff"; //$NON-NLS-1$

	private URI baseLocation;

	public Diff(URI baseLocation, Repository db) throws URISyntaxException, CoreException {
		super(BaseToCloneConverter.getCloneLocation(baseLocation, BaseToCloneConverter.DIFF), db);
		this.baseLocation = baseLocation;
	}

	public JSONObject toJSON() throws JSONException, URISyntaxException, IOException {
		IPath path = new Path(baseLocation.getPath()).removeFirstSegments(2);
		JSONObject diff = new JSONObject();
		diff.put(ProtocolConstants.KEY_LOCATION, baseLocation);
		diff.put(GitConstants.KEY_COMMIT_OLD, getOldLocation(baseLocation, path));
		diff.put(GitConstants.KEY_COMMIT_NEW, getNewLocation(baseLocation, path));
		diff.put(GitConstants.KEY_COMMIT_BASE, getBaseLocation(baseLocation, db, path));
		diff.put(ProtocolConstants.KEY_TYPE, TYPE);
		return diff;
	}

	private URI getOldLocation(URI location, IPath path) throws URISyntaxException {
		String scope = path.segment(0);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				// TODO:
				throw new IllegalArgumentException();
			}
			// TODO: decode commits[0]
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

	private URI getNewLocation(URI location, IPath path) throws URISyntaxException {
		String scope = path.segment(0);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				// TODO:
				throw new IllegalArgumentException();
			}
			// TODO: decode commits[1]
			IPath p = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(commits[1]).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), "parts=body", null); //$NON-NLS-1$
		} else if (scope.equals(GitConstants.KEY_DIFF_CACHED)) {
			IPath p = new Path(GitServlet.GIT_URI + '/' + Index.RESOURCE).append(path.removeFirstSegments(1));
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), p.toString(), null, null);
		} else {
			/* including scope.equals(GitConstants.KEY_DIFF_DEFAULT */
			return new URI(location.getScheme(), location.getUserInfo(), location.getHost(), location.getPort(), path.removeFirstSegments(1).makeAbsolute().toString(), null, null);
		}
	}

	private URI getBaseLocation(URI location, Repository db, IPath path) throws URISyntaxException, IOException {
		String scope = path.segment(0);
		if (scope.contains("..")) { //$NON-NLS-1$
			String[] commits = scope.split("\\.\\."); //$NON-NLS-1$
			if (commits.length != 2) {
				// TODO:
				throw new IllegalArgumentException();
			}
			// TODO: decode commits[]

			ThreeWayMerger merger = new ResolveMerger(db) {
				protected boolean mergeImpl() throws IOException {
					// do nothing
					return false;
				}
			};
			// use #merge to set sourceObjects
			merger.merge(new ObjectId[] {db.resolve(commits[0]), db.resolve(commits[1])});
			RevCommit baseCommit = merger.getBaseCommit(0, 1);

			IPath p = new Path(GitServlet.GIT_URI + '/' + Commit.RESOURCE).append(baseCommit.getId().getName()).append(path.removeFirstSegments(1));
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
