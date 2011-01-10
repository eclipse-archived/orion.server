/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.webide.server.search;

import java.io.*;
import java.net.*;
import javax.servlet.ServletException;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.*;
import org.apache.solr.servlet.SolrServlet;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.*;
import org.eclipse.e4.internal.webide.BundleEntryHttpContext;
import org.eclipse.e4.internal.webide.server.IOUtilities;
import org.eclipse.e4.internal.webide.server.IWebResourceDecorator;
import org.eclipse.e4.internal.webide.server.servlets.Activator;
import org.eclipse.e4.internal.webide.server.servlets.ProtocolConstants;
import org.eclipse.e4.webide.server.LogHelper;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.*;
import org.osgi.service.http.*;
import org.osgi.util.tracker.ServiceTracker;

public class SearchActivator implements BundleActivator, IWebResourceDecorator {
	private static final String INDEX_GENERATION_FILE = "index.generation";//$NON-NLS-1$
	/**
	 * Indicates the version number of the eclipse web search index. This version
	 * should be incremented whenever there are breaking changes to the indexing
	 * schema or format.
	 */
	private static final int CURRENT_INDEX_GENERATION = 3;

	private class HttpServiceTracker extends ServiceTracker<HttpService, HttpService> {

		public HttpServiceTracker(BundleContext context) {
			super(context, HttpService.class.getName(), null);
		}

		public HttpService addingService(ServiceReference<HttpService> reference) {
			HttpService httpService = super.addingService(reference); // calls context.getService(reference);
			if (httpService == null)
				return null;
			HttpContext httpContext = new BundleEntryHttpContext(context.getBundle());

			try {
				httpService.registerServlet(LOCATION_SEARCH_SERVLET, new SolrServlet(), null, httpContext);
			} catch (ServletException e) {
				e.printStackTrace();
			} catch (NamespaceException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return httpService;
		}

		public void removedService(ServiceReference<HttpService> reference, HttpService httpService) {
			httpService.unregister(LOCATION_SEARCH_SERVLET);
			super.removedService(reference, httpService); // calls context.ungetService(reference);
		}
	}

	private static BundleContext context;
	public static final String LOCATION_SEARCH_SERVLET = "/solrsearch"; //$NON-NLS-1$
	public static final boolean DEBUG = true;
	public static final String PI_SEARCH = "org.eclipse.e4.webide.server.search"; //$NON-NLS-1$
	private ServiceTracker<HttpService, HttpService> httpServiceTracker;
	private Indexer indexer;
	private CoreContainer solrContainer;
	private ServiceRegistration<IWebResourceDecorator> searchDecoratorRegistration;

	static BundleContext getContext() {
		return context;
	}

	public Indexer getIndexer() {
		return indexer;
	}

	public void addAtributesFor(URI resource, JSONObject representation) {
		IPath resourcePath = new Path(resource.getPath());
		//currently we only know how to search the file and workspace services
		if (resourcePath.segmentCount() == 0)
			return;
		String service = resourcePath.segment(0);
		if (!("file".equals(service) || "workspace".equals(service))) //$NON-NLS-1$ //$NON-NLS-2$
			return;
		try {
			//we can also augment with a query argument that includes the resource path
			URI result = new URI(resource.getScheme(), resource.getUserInfo(), resource.getHost(), resource.getPort(), "/solrsearch", "wt=json&fl=Id,Name,Length,Directory,LastModified,Location&hl=true&q=", null);
			representation.put(ProtocolConstants.KEY_SEARCH_LOCATION, result.toString());
		} catch (URISyntaxException e) {
			LogHelper.log(e);
		} catch (JSONException e) {
			//key and value are well-formed strings so this should not happen
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		SearchActivator.context = bundleContext;
		httpServiceTracker = new HttpServiceTracker(context);
		httpServiceTracker.open();
		SolrServer server = createServer();
		if (server != null) {
			indexer = new Indexer(server);
			indexer.schedule();
		}
		searchDecoratorRegistration = context.registerService(IWebResourceDecorator.class, this, null);
	}

	/**
	 * Creates and returns a search server instance. Returns null if there was a failure instantiating the server.
	 */
	private SolrServer createServer() {
		try {
			File rootFile = Activator.getDefault().getPlatformLocation().toFile();
			File baseDir = new File(rootFile, ".metadata/.plugins/" + PI_SEARCH); //$NON-NLS-1$
			//discard all server data if the index generation has changed
			if (readIndexGeneration(baseDir) != CURRENT_INDEX_GENERATION) {
				delete(baseDir);
				writeIndexGeneration(baseDir);
			}
			File configFile = createSolrConfig(baseDir);
			String solrDataDir = baseDir.toString();
			solrContainer = new CoreContainer(solrDataDir, configFile);
			CoreDescriptor descriptor = new CoreDescriptor(solrContainer, "Eclipse Web Search", solrDataDir); //$NON-NLS-1$
			descriptor.setDataDir(solrDataDir.toString() + File.separatorChar + "data"); //$NON-NLS-1$
			SolrCore solrCore = solrContainer.create(descriptor);
			solrContainer.register(solrCore, false);
			SolrServer server = new EmbeddedSolrServer(solrContainer, "Eclipse Web Search"); //$NON-NLS-1$
			return server;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
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

	private void delete(File baseDir) {
		try {
			EFS.getStore(baseDir.toURI()).delete(EFS.NONE, null);
		} catch (CoreException e) {
			LogHelper.log(e);
		}
	}

	/**
	 * Returns the generation number of the current index on disk.
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
			//ignore and return false below
		} finally {
			IOUtilities.safeClose(in);
		}
		return -1;
	}

	/**
	 * Ensure solr configuration files exist. Copy them from the search plugin if necessary.
	 * Returns the solrconfig.xml file.
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
	 * Create a configuration file expected by solr (either solrconfig.xml or schema.xml).
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

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		searchDecoratorRegistration.unregister();
		if (solrContainer != null) {
			solrContainer.shutdown();
			solrContainer = null;
		}
		if (indexer != null) {
			indexer.cancel();
			indexer.join();
			indexer = null;
		}
		SearchActivator.context = null;
	}

}
