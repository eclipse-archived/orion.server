package org.eclipse.orion.internal.server.hosting;

import java.util.Map;

/**
 * Keeps an in-memory-only table of hosted sites.  A hosted site is a record of a site configuration that 
 * that has been started on a server.
 */
public class HostedSitesTable {

}

class Entry {
	String devServer;
	String siteConfigurationId;
	String userId;

	String host;
	Map<String, String> mappings;
}
