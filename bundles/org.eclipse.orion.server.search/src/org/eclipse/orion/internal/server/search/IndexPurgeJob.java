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
package org.eclipse.orion.internal.server.search;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.resources.FileLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The IndexPurgeJob is responsible for cleaning up the indexes that that are
 * no longer required. The job crawls the indexes and purges those whose corresponding
 * resources no longer present in the file system.
 */
public class IndexPurgeJob extends Job {

	private static final long DEFAULT_DELAY = 30000;//3 minutes
	private static final long PAGE_SIZE = 1000;
	private final SolrServer server;
	private File lockFile;

	public IndexPurgeJob(SolrServer server, File indexRoot) {
		super("Purging Index"); //$NON-NLS-1$
		this.server = server;
		this.lockFile = new File(indexRoot, "lockp.txt");
		setSystem(true);
	}

	@Override
	public boolean belongsTo(Object family) {
		return SearchActivator.JOB_FAMILY.equals(family);
	}

	private void checkCanceled(IProgressMonitor monitor) {
		if (monitor.isCanceled())
			throw new OperationCanceledException();
	}

	public void ensureUpdated() {
		schedule(DEFAULT_DELAY);
	}

	private SolrQuery findAllQuery() {
		SolrQuery query = new SolrQuery();
		query.setParam(CommonParams.ROWS, Long.toString(PAGE_SIZE));
		//we only need to return the id for each match
		query.setFields(ProtocolConstants.KEY_ID);
		query.setQuery("*:*"); //$NON-NLS-1$
		return query;
	}

	private void handleIndexingFailure(Throwable t) {
		LogHelper.log(new Status(IStatus.ERROR, SearchActivator.PI_SEARCH, "Error during search index purge", t)); //$NON-NLS-1$
	}

	private void markStaleIndexes(SolrDocumentList list, List<String> listIds) {
		Iterator<SolrDocument> iterator = list.iterator();
		while (iterator.hasNext()) {
			SolrDocument doc = iterator.next();
			URI uri;
			try {
				uri = new URI((String) doc.getFieldValue(ProtocolConstants.KEY_ID));
				IFileStore file = null;

				if (uri.isAbsolute()) {
					file = EFS.getLocalFileSystem().getStore(URIUtil.toPath(uri));
				} else {
					file = EFS.getStore(uri);
				}

				if (!file.fetchInfo().exists())
					listIds.add((String) doc.getFieldValue(ProtocolConstants.KEY_ID));

			} catch (Exception e) {
				handleIndexingFailure(e);
				continue;
			}
		}
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		Logger logger = LoggerFactory.getLogger(Indexer.class);
		if (logger.isDebugEnabled())
			logger.debug("Purging indexes"); //$NON-NLS-1$
		long start = System.currentTimeMillis();
		FileLocker lock = new FileLocker(lockFile);
		SolrQuery query = findAllQuery();
		try {
			if (!lock.tryLock()) {
				if (logger.isInfoEnabled()) {
					logger.info("Search index purge: another process is currently running");
				}
				schedule(DEFAULT_DELAY * 2);
				return Status.OK_STATUS;
			}
			QueryResponse solrResponse = this.server.query(query);
			SolrDocumentList result = solrResponse.getResults();
			long numFound = result.getNumFound();
			long processed = 0;
			List<String> listIds = new ArrayList<String>();
			if (numFound > processed) {
				while (true) {
					checkCanceled(monitor);
					markStaleIndexes(result, listIds);
					processed += PAGE_SIZE;
					if (processed >= numFound)
						break;
					query.setParam(CommonParams.START, Long.toString(processed));
					solrResponse = this.server.query(query);
					result = solrResponse.getResults();
					// New indexes may have been added, perhaps
					numFound = result.getNumFound();
				}
			}

			checkCanceled(monitor);
			if (listIds.size() > 0) {
				this.server.deleteById(listIds);
				this.server.commit();
			}
			if (logger.isDebugEnabled())
				logger.debug("\tPurged: " + listIds.size()); //$NON-NLS-1$
		} catch (OperationCanceledException e) {
			//ignore and fall through
		} catch (Exception e) {
			handleIndexingFailure(e);
		} finally {
			if (lock.isValid()) {
				lock.release();
			}
		}
		long duration = System.currentTimeMillis() - start;
		if (logger.isDebugEnabled())
			logger.debug("Purge job took " + duration + "ms"); //$NON-NLS-1$ //$NON-NLS-2$

		//throttle scheduling frequency so the job never runs more than 5% of the time
		long delay = Math.max(DEFAULT_DELAY, duration * 20);
		schedule(delay);
		return Status.OK_STATUS;
	}
}