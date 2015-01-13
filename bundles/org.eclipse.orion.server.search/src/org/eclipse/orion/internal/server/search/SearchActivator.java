/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.search;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrCore;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.orion.internal.server.search.grep.SearchJob;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.IWebResourceDecorator;
import org.eclipse.orion.server.core.LogHelper;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.PreferenceHelper;
import org.eclipse.orion.server.core.ProtocolConstants;
import org.eclipse.orion.server.core.ServerConstants;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class SearchActivator implements BundleActivator, IWebResourceDecorator {
	private static BundleContext context;
	/**
	 * Indicates the version number of the Orion search index. This
	 * version should be incremented whenever there are breaking changes to the
	 * indexing schema or format.
	 */
	private static final int CURRENT_INDEX_GENERATION = 16;

	private static final String INDEX_GENERATION_FILE = "index.generation";//$NON-NLS-1$
	private static SearchActivator instance;
	public static final String PI_SEARCH = "org.eclipse.orion.server.core.search"; //$NON-NLS-1$
	/**
	 * A family for all jobs related to indexing. Used to join jobs on shutdown.
	 */
	public static final Object JOB_FAMILY = new Object();
	private Indexer indexer;
	private IndexPurgeJob purgeJob;
	private ServiceRegistration<IWebResourceDecorator> searchDecoratorRegistration;
	private SolrServer server;
	private SolrCore solrCore;
	private CoreContainer solrContainer;
	private File baseDir;

	static BundleContext getContext() {
		return context;
	}

	public static SearchActivator getInstance() {
		return instance;
	}

	public SearchActivator() {
		super();
		instance = this;
	}

	public void addAtributesFor(HttpServletRequest request, URI resource, JSONObject representation) {
		String service = request.getServletPath();
		if (!("/file".equals(service) || "/workspace".equals(service))) //$NON-NLS-1$ //$NON-NLS-2$
			return;
		try {
			// we can also augment with a query argument that includes the resource path
			URI result = new URI(resource.getScheme(), resource.getAuthority(), "/filesearch", "q=", null); //$NON-NLS-1$//$NON-NLS-2$
			representation.put(ProtocolConstants.KEY_SEARCH_LOCATION, result);
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		} catch (JSONException e) {
			// key and value are well-formed strings so this should not happen
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates and returns a search server instance. Returns null if there was a
	 * failure instantiating the server.
	 */
	private void createServer() {
		try {
			String prop = PreferenceHelper.getString(ServerConstants.CONFIG_SEARCH_INDEX_LOCATION);
			if (prop != null) {
				IPath rootPath = new Path(prop);
				File rootFile = rootPath.toFile();
				baseDir = new File(rootFile, PI_SEARCH + "/v" + CURRENT_INDEX_GENERATION); //$NON-NLS-1$
			} else {
				IPath rootPath = OrionConfiguration.getPlatformLocation();
				File rootFile = rootPath.toFile();
				baseDir = new File(rootFile, ".metadata/.plugins/" + PI_SEARCH + "/v" + CURRENT_INDEX_GENERATION); //$NON-NLS-1$
			}

			// discard all server data if the index generation has changed
			if (readIndexGeneration(baseDir) != CURRENT_INDEX_GENERATION) {
				delete(baseDir);
				writeIndexGeneration(baseDir);
			}
			createSolrConfig(baseDir);
			String solrDataDir = baseDir.toString();
			solrContainer = new CoreContainer(solrDataDir);
			CoreDescriptor descriptor = new CoreDescriptor(solrContainer, "Eclipse Web Search", solrDataDir); //$NON-NLS-1$
			descriptor.setDataDir(solrDataDir.toString() + File.separatorChar + "data"); //$NON-NLS-1$
			solrCore = solrContainer.create(descriptor);
			solrContainer.register(solrCore, false);
			server = new EmbeddedSolrServer(solrContainer, "Eclipse Web Search"); //$NON-NLS-1$
		} catch (Exception e) {
			LogHelper.log(e);
		}
	}

	/**
	 * Ensure solr configuration files exist. Copy them from the search plugin
	 * if necessary. Returns the solrconfig.xml file.
	 */
	private File createSolrConfig(File baseDir) throws FileNotFoundException, IOException {
		File configDir = new File(baseDir, "conf"); //$NON-NLS-1$
		configDir.mkdirs();
		File configFile = new File(configDir, "solrconfig.xml"); //$NON-NLS-1$
		createSolrFile(configFile);
		createSolrFile(new File(configDir, "schema.xml")); //$NON-NLS-1$
		createSolrFile(new File(configDir, "synonyms.txt")); //$NON-NLS-1$
		createSolrFile(new File(configDir, "stopwords.txt")); //$NON-NLS-1$
		createSolrFile(new File(configDir, "protwords.txt")); //$NON-NLS-1$
		createSolrFile(new File(configDir, "elevate.xml")); //$NON-NLS-1$
		return configFile;

	}

	/**
	 * Create a configuration file expected by solr (either solrconfig.xml or
	 * schema.xml).
	 * 
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private void createSolrFile(File solrFile) throws FileNotFoundException, IOException {
		if (solrFile.exists())
			return;
		URL source = getClass().getClassLoader().getResource("solrconf/" + solrFile.getName()); //$NON-NLS-1$
		source = FileLocator.resolve(source);
		IOUtilities.pipe(source.openStream(), new FileOutputStream(solrFile), true, true);
	}

	private void delete(File baseDir) {
		try {
			EFS.getStore(baseDir.toURI()).delete(EFS.NONE, null);
		} catch (CoreException e) {
			LogHelper.log(e);
		}
	}

	SolrServer getSolrServer() {
		return server;
	}

	SolrCore getSolrCore() {
		return solrCore;
	}

	/**
	 * Returns the generation number of the current index on disk.
	 * 
	 * @return the current index generation, or -1 if no index was found.
	 */
	private int readIndexGeneration(File baseDir) {
		File generationFile = new File(baseDir, INDEX_GENERATION_FILE);
		if (!generationFile.exists())
			return -1;
		DataInputStream in = null;
		try {
			in = new DataInputStream(new FileInputStream(generationFile));
			int generation = Integer.valueOf(in.readUTF());
			return generation;
		} catch (Exception e) {
			// ignore and return false below
		} finally {
			IOUtilities.safeClose(in);
		}
		return -1;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	public void start(BundleContext bundleContext) throws Exception {
		SearchActivator.context = bundleContext;
		createServer();
		if (server != null) {
			if (PreferenceHelper.getString(ServerConstants.CONFIG_GREP_SEARCH_ENABLED, "false").equals("false")) {
				indexer = new Indexer(server, baseDir);
				indexer.schedule();

				purgeJob = new IndexPurgeJob(server, baseDir);
				purgeJob.schedule();
			}
		}
		searchDecoratorRegistration = context.registerService(IWebResourceDecorator.class, this, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		searchDecoratorRegistration.unregister();
		if (solrContainer != null) {
			solrContainer.shutdown();
			solrContainer = null;
		}
		if (indexer != null) {
			indexer.cancel();
			indexer = null;
		}
		if (purgeJob != null) {
			purgeJob.cancel();
			purgeJob = null;
		}
		//wait for all indexing jobs to complete
		Job.getJobManager().join(JOB_FAMILY, null);
		SearchActivator.context = null;
		//cancel all the running search jobs
		Job.getJobManager().cancel(SearchJob.FAMILY);
		Job.getJobManager().join(SearchJob.FAMILY, null);
	}

	private void writeIndexGeneration(File baseDir) {
		baseDir.mkdirs();
		File generationFile = new File(baseDir, INDEX_GENERATION_FILE);
		DataOutputStream out = null;
		try {
			out = new DataOutputStream(new FileOutputStream(generationFile));
			out.writeUTF(Integer.toString(CURRENT_INDEX_GENERATION));
		} catch (IOException e) {
			String msg = "Error writing search index generation number. Subsequent restarts will discard and rebuild search index from scratch"; //$NON-NLS-1$
			LogHelper.log(new Status(IStatus.ERROR, SearchActivator.PI_SEARCH, msg, e));
		} finally {
			IOUtilities.safeClose(out);
		}
	}

	/**
	 * Helper method for test suites. This method will block until the next indexer run completes.
	 */
	public void testWaitForIndex() {
		if (indexer == null) {
			return;
		}
		try {
			if (indexer.getState() == Job.RUNNING) {
				indexer.join();
			} else {
				//cancel to wake up a sleeping indexer
				indexer.cancel();
				indexer.schedule();
				indexer.join();
			}
		} catch (InterruptedException e) {
			//just return
		}
	}

}
