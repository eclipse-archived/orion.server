/*******************************************************************************
 * Copyright (c) 2010, 2013 IBM Corporation and others.
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
import java.net.URISyntaxException;
import java.util.*;
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
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.*;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The indexer is responsible for keeping the solr/lucene index up to date.
 * It currently does this by naively polling the file system on a periodic basis.
 */
public class Indexer extends Job {

	/**
	 * The minimum delay between indexing runs
	 */
	private static final long DEFAULT_DELAY = 60000;//one minute
	/**
	 * The minimum delay between indexing runs when the server is idle.
	 */
	private static final long IDLE_DELAY = 300000;//five minutes

	private static final long MAX_SEARCH_SIZE = 300000;//don't index files larger than 300,000 bytes
	//private static final List<String> IGNORED_FILE_TYPES = Arrays.asList("png", "jpg", "gif", "bmp", "pdf", "tiff", "class", "so", "zip", "jar", "tar");
	private final List<String> INDEXED_FILE_TYPES;
	private final SolrServer server;

	public Indexer(SolrServer server) {
		super("Indexing"); //$NON-NLS-1$
		this.server = server;
		setSystem(true);
		INDEXED_FILE_TYPES = Arrays.asList("css", "js", "html", "txt", "xml", "java", "properties", "php", "htm", "project", "conf", "pl", "sh", "text", "xhtml", "mf", "manifest", "md", "yaml", "yml");
		Collections.sort(INDEXED_FILE_TYPES);
	}

	@Override
	public boolean belongsTo(Object family) {
		return SearchActivator.JOB_FAMILY.equals(family);
	}

	/**
	 * Adds all files in the given directory to the provided list.
	 */
	private void collectFiles(IFileStore dir, List<IFileStore> files) {
		try {
			IFileStore[] children = dir.childStores(EFS.NONE, null);
			for (IFileStore child : children) {
				if (!child.getName().startsWith(".") && !child.fetchInfo().getAttribute(EFS.ATTRIBUTE_SYMLINK)) { //$NON-NLS-1$
					IFileInfo info = child.fetchInfo();
					if (info.isDirectory())
						collectFiles(child, files);
					else
						files.add(child);
				}
			}
		} catch (CoreException e) {
			handleIndexingFailure(e, dir);
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
			handleIndexingFailure(e, file);
		} catch (CoreException e) {
			handleIndexingFailure(e, file);
		}
		return writer.toString();
	}

	/**
	 * Helper method for handling failures that occur while indexing.
	 */
	private void handleIndexingFailure(Throwable t, IFileStore file) {
		String message;
		if (file != null) {
			message = NLS.bind("Error during searching indexing on file: {0}", file.toString());
		} else {
			message = "Error during searching indexing";

		}
		LogHelper.log(new Status(IStatus.ERROR, SearchActivator.PI_SEARCH, message, t));
	}

	/**
	 * Runs an indexer pass over a user. Returns the number of documents indexed.
	 */
	private int indexUser(String userId, IProgressMonitor monitor, List<SolrInputDocument> documents) {
		int indexed = 0;
		try {
			final IMetaStore store = OrionConfiguration.getMetaStore();
			UserInfo user = store.readUser(userId);
			List<String> workspaceIds = user.getWorkspaceIds();
			SubMonitor progress = SubMonitor.convert(monitor, workspaceIds.size());
			for (String workspaceId : workspaceIds) {
				WorkspaceInfo workspace = store.readWorkspace(workspaceId);
				indexed += indexWorkspace(user, workspace, progress.newChild(1), documents);
			}
		} catch (CoreException e) {
			handleIndexingFailure(e, null);
		}
		return indexed;
	}

	/**
	 * Runs an indexer pass over a workspace. Returns the number of documents indexed.
	 */
	private int indexWorkspace(UserInfo user, WorkspaceInfo workspace, SubMonitor monitor, List<SolrInputDocument> documents) {
		int indexed = 0;
		IMetaStore store = OrionConfiguration.getMetaStore();
		for (String projectName : workspace.getProjectNames()) {
			try {
				indexed += indexProject(user, workspace, store.readProject(workspace.getUniqueId(), projectName), monitor, documents);
			} catch (CoreException e) {
				handleIndexingFailure(e, null);
				//continue to next project
			}
		}
		return indexed;
	}

	private int indexProject(UserInfo user, WorkspaceInfo workspace, ProjectInfo project, SubMonitor monitor, List<SolrInputDocument> documents) {
		Logger logger = LoggerFactory.getLogger(Indexer.class);
		if (logger.isDebugEnabled())
			logger.debug("Indexing project id: " + project.getUniqueId() + " name: " + project.getFullName()); //$NON-NLS-1$ //$NON-NLS-2$
		checkCanceled(monitor);
		IFileStore projectStore;
		try {
			projectStore = project.getProjectStore();
		} catch (CoreException e) {
			//TODO implement indexing of remote content
			handleIndexingFailure(e, null);
			return 0;
		}
		//don't index remote file systems for now
		if (!EFS.getLocalFileSystem().getScheme().equals(projectStore.getFileSystem().getScheme()))
			return 0;
		String encodedProjectName;
		try {
			//project location field is an encoded URI
			encodedProjectName = new URI(null, null, project.getFullName(), null).toString();
		} catch (URISyntaxException e) {
			//UTF-8 should never be unsupported
			handleIndexingFailure(e, projectStore);
			return 0;
		}
		IPath projectLocation = new Path(Activator.LOCATION_FILE_SERVLET).append(workspace.getUniqueId()).append(encodedProjectName).addTrailingSeparator();
		//gather all files
		int projectLocationLength = projectStore.toURI().toString().length();
		final List<IFileStore> toIndex = new ArrayList<IFileStore>();
		collectFiles(projectStore, toIndex);
		int unmodifiedCount = 0, indexedCount = 0;
		//add each file to the index
		for (IFileStore file : toIndex) {
			checkCanceled(monitor);
			IFileInfo fileInfo = file.fetchInfo();
			if (!isModified(file, fileInfo)) {
				unmodifiedCount++;
				continue;
			}
			indexedCount++;
			SolrInputDocument doc = new SolrInputDocument();
			doc.addField(ProtocolConstants.KEY_ID, file.toURI().toString());
			doc.addField(ProtocolConstants.KEY_NAME, fileInfo.getName());
			doc.addField(ProtocolConstants.KEY_NAME_LOWERCASE, fileInfo.getName());//Lucene will do lower-casing
			doc.addField(ProtocolConstants.KEY_LENGTH, Long.toString(fileInfo.getLength()));
			doc.addField(ProtocolConstants.KEY_DIRECTORY, Boolean.toString(fileInfo.isDirectory()));
			doc.addField(ProtocolConstants.KEY_LAST_MODIFIED, Long.toString(fileInfo.getLastModified()));
			//we add the server-relative location so the server can be moved without affecting the index
			String projectRelativePath = file.toURI().toString().substring(projectLocationLength);
			IPath fileLocation = projectLocation.append(projectRelativePath);
			doc.addField(ProtocolConstants.KEY_LOCATION, fileLocation.toString());
			String projectName = project.getFullName();
			//Projects with no name are due to an old bug where project metadata was not deleted  see bug 367333.
			if (projectName == null)
				continue;
			doc.addField(ProtocolConstants.KEY_PATH, new Path(projectName).append(projectRelativePath));
			//don't index body of non-text files
			if (!skip(fileInfo))
				doc.addField("Text", getContentsAsString(file)); //$NON-NLS-1$
			doc.addField(ProtocolConstants.KEY_USER_NAME, user.getUniqueId());
			try {
				server.add(doc);
			} catch (Exception e) {
				handleIndexingFailure(e, file);
			}
		}
		try {
			server.commit();
		} catch (Exception e) {
			handleIndexingFailure(e, null);
		}
		if (logger.isDebugEnabled())
			logger.debug("\tIndexed: " + indexedCount + " Unchanged:  " + unmodifiedCount); //$NON-NLS-1$ //$NON-NLS-2$
		return indexedCount;
	}

	private boolean skip(IFileInfo fileInfo) {
		if (fileInfo.getLength() > MAX_SEARCH_SIZE)
			return true;
		//skip files with no extension, or known binary file type extensions
		String extension = new Path(fileInfo.getName()).getFileExtension();
		if (extension == null || (Collections.binarySearch(INDEXED_FILE_TYPES, extension.toLowerCase()) < 0))
			return true;
		return false;
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
			handleIndexingFailure(e, file);
			//attempt to re-index
			return true;
		}
	}

	private void checkCanceled(IProgressMonitor monitor) {
		if (monitor.isCanceled())
			throw new OperationCanceledException();
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		Logger logger = LoggerFactory.getLogger(Indexer.class);
		IMetaStore metaStore;
		try {
			metaStore = OrionConfiguration.getMetaStore();
		} catch (IllegalStateException e) {
			//bundle providing metastore might not have started yet
			if (logger.isDebugEnabled())
				logger.debug("Search indexer waiting for metadata service");
			schedule(5000);
			return Status.OK_STATUS;
		}
		long start = System.currentTimeMillis();
		int indexed = 0;
		try {
			List<String> userIds = metaStore.readAllUsers();
			SubMonitor progress = SubMonitor.convert(monitor, userIds.size());
			List<SolrInputDocument> documents = new ArrayList<SolrInputDocument>();
			indexed = 0;
			for (String userId : userIds) {
				indexed += indexUser(userId, progress.newChild(1), documents);
			}
		} catch (CoreException e) {
			handleIndexingFailure(e, null);
		}
		long duration = System.currentTimeMillis() - start;
		if (logger.isDebugEnabled())
			logger.debug("Indexed " + indexed + " documents in " + duration + "ms"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		//reschedule the indexing - throttle so the job never runs more than 10% of the time
		long delay = Math.max(DEFAULT_DELAY, duration * 10);
		//never wait longer than max idle delay
		delay = Math.min(delay, IDLE_DELAY);
		//if there was nothing to index then back off for awhile
		delay = Math.max(delay, IDLE_DELAY);
		if (logger.isDebugEnabled())
			logger.debug("Rescheduling indexing in " + delay + "ms"); //$NON-NLS-1$//$NON-NLS-2$
		schedule(delay);
		return Status.OK_STATUS;
	}
}
