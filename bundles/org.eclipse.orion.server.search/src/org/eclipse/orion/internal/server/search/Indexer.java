/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.internal.server.core.IOUtilities;
import org.eclipse.orion.internal.server.servlets.Activator;
import org.eclipse.orion.internal.server.servlets.ProtocolConstants;
import org.eclipse.orion.internal.server.servlets.workspace.WebProject;
import org.eclipse.orion.server.core.LogHelper;

/**
 * The indexer is responsible for keeping the solr/lucene index up to date.
 * It currently does this by naively polling the file system on a periodic basis.
 */
public class Indexer extends Job {

	private static final long DEFAULT_DELAY = 60000;//one minute
	private static final long MAX_SEARCH_SIZE = 100000;//don't index files larger than 100,000 bytes
	private final SolrServer server;

	public Indexer(SolrServer server) {
		super("Indexing");
		this.server = server;
		setSystem(true);
	}

	/**
	 * Adds all files in the given directory to the provided list.
	 */
	private void collectFiles(IFileStore dir, List<IFileStore> files) {
		try {
			IFileStore[] children = dir.childStores(EFS.NONE, null);
			for (IFileStore child : children) {
				if (!child.getName().startsWith(".")) {
					IFileInfo info = child.fetchInfo();
					if (info.isDirectory())
						collectFiles(child, files);
					else
						files.add(child);
				}
			}
		} catch (CoreException e) {
			handleIndexingFailure(e);
		}
	}

	public void ensureUpdated() {
		schedule(DEFAULT_DELAY);
	}

	private String getContentsAsString(IFileStore file) {
		StringWriter writer = new StringWriter();
		try {
			IOUtilities.pipe(new InputStreamReader(file.openInputStream(EFS.NONE, null)), writer, true, false);
		} catch (IOException e) {
			handleIndexingFailure(e);
		} catch (CoreException e) {
			handleIndexingFailure(e);
		}
		return writer.toString();
	}

	/**
	 * Helper method for handling failures that occur while indexing.
	 */
	private void handleIndexingFailure(Throwable t) {
		LogHelper.log(new Status(IStatus.ERROR, SearchActivator.PI_SEARCH, "Error during search indexing", t)); //$NON-NLS-1$
	}

	private void indexProject(WebProject project, SubMonitor monitor, List<SolrInputDocument> documents) {
		if (SearchActivator.DEBUG)
			System.out.println("Indexing project id: " + project.getId() + " name: " + project.getName()); //$NON-NLS-1$ //$NON-NLS-2$
		checkCanceled(monitor);
		URI location = project.getContentLocation();
		IFileStore projectStore;
		IPath projectLocation;
		try {
			if (location.isAbsolute()) {
				projectStore = EFS.getStore(location);
				//location will be based on alias registered with file service
				projectLocation = new Path(Activator.LOCATION_FILE_SERVLET).append(project.getId());
			} else {
				//there is no scheme but it could still be an absolute path
				IPath localPath = new Path(location.getPath());
				if (localPath.isAbsolute()) {
					projectStore = EFS.getLocalFileSystem().getStore(localPath);
					//location will be based on alias registered with file service
					projectLocation = new Path(Activator.LOCATION_FILE_SERVLET).append(project.getId());
				} else {
					//treat relative location as relative to the file system root
					URI rootLocation = Activator.getDefault().getRootLocationURI();
					IFileStore root = EFS.getStore(rootLocation);
					projectStore = root.getChild(location.toString());
					projectLocation = new Path(Activator.LOCATION_FILE_SERVLET).append(localPath.segment(0));
				}
			}
		} catch (CoreException e) {
			//TODO implement indexing of remote content
			handleIndexingFailure(e);
			return;
		}
		//gather all files
		int projectLocationLength = projectStore.toURI().toString().length();
		final List<IFileStore> toIndex = new ArrayList<IFileStore>();
		collectFiles(projectStore, toIndex);
		int unmodifiedCount = 0, indexedCount = 0;
		for (IFileStore file : toIndex) {
			checkCanceled(monitor);
			IFileInfo fileInfo = file.fetchInfo();
			if (fileInfo.getLength() > MAX_SEARCH_SIZE)
				continue;
			if (!isModified(file, fileInfo)) {
				unmodifiedCount++;
				continue;
			}
			indexedCount++;
			SolrInputDocument doc = new SolrInputDocument();
			doc.addField(ProtocolConstants.KEY_ID, file.toURI().toString());
			doc.addField(ProtocolConstants.KEY_NAME, fileInfo.getName());
			doc.addField(ProtocolConstants.KEY_LENGTH, Long.toString(fileInfo.getLength()));
			doc.addField(ProtocolConstants.KEY_DIRECTORY, Boolean.toString(fileInfo.isDirectory()));
			doc.addField(ProtocolConstants.KEY_LAST_MODIFIED, Long.toString(fileInfo.getLastModified()));
			//we add the server-relative location so the server can be moved without affecting the index
			IPath fileLocation = projectLocation.append(file.toURI().toString().substring(projectLocationLength));
			doc.addField(ProtocolConstants.KEY_LOCATION, fileLocation.toString());
			doc.addField("Text", getContentsAsString(file)); //$NON-NLS-1$
			try {
				server.add(doc);
			} catch (Exception e) {
				handleIndexingFailure(e);
			}
		}
		try {
			server.commit();
		} catch (Exception e) {
			handleIndexingFailure(e);
		}
		if (SearchActivator.DEBUG)
			System.out.println("\tIndexed: " + indexedCount + " Unchanged:  " + unmodifiedCount); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private boolean isModified(IFileStore file, IFileInfo fileInfo) {
		try {
			//if there is no match, then the file last modified doesn't match last index so assume it was modified
			StringBuffer qString = new StringBuffer(ProtocolConstants.KEY_ID);
			qString.append(':');
			qString.append(ClientUtils.escapeQueryChars(file.toURI().toString()));
			qString.append(" AND "); //$NON-NLS-1$
			qString.append(ProtocolConstants.KEY_LAST_MODIFIED);
			qString.append(':');
			qString.append(Long.toString(fileInfo.getLastModified()));
			SolrQuery query = new SolrQuery(qString.toString());
			query.setParam(CommonParams.FL, ProtocolConstants.KEY_ID);
			QueryResponse response = server.query(query);
			return response.getResults().getNumFound() == 0;
		} catch (SolrServerException e) {
			handleIndexingFailure(e);
			//attempt to reindex
			return true;
		}
	}

	private void checkCanceled(IProgressMonitor monitor) {
		if (monitor.isCanceled())
			throw new OperationCanceledException();
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		long start = System.currentTimeMillis();
		List<WebProject> projects = WebProject.allProjects();
		SubMonitor progress = SubMonitor.convert(monitor, projects.size());
		List<SolrInputDocument> documents = new ArrayList<SolrInputDocument>();
		for (WebProject project : projects) {
			indexProject(project, progress.newChild(1), documents);
		}
		long duration = System.currentTimeMillis() - start;
		if (SearchActivator.DEBUG)
			System.out.println("Indexed " + projects.size() + " projects in " + duration + "ms"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		//reschedule the indexing - throttle if it takes too long
		long delay = Math.max(DEFAULT_DELAY, duration);
		if (SearchActivator.DEBUG)
			System.out.println("Rescheduling indexing in " + delay + "ms"); //$NON-NLS-1$//$NON-NLS-2$
		schedule(delay);
		return Status.OK_STATUS;
	}

}
