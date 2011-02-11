package org.eclipse.orion.internal.server.hosting;

import java.util.Map;

/**
 * Keeps an in-memory-only table of hosted sites.  A hosted site is a record of 
 * a site configuration that is started on a server.
 */
public class HostedSitesTable {

}

class Entry {
	String devServer;
	String userId;
	String siteConfigurationId;

	String host;
	Map<String, String> mappings;
}
