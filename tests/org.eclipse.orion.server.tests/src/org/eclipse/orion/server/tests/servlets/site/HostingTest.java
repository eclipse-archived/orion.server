package org.eclipse.orion.server.tests.servlets.site;

import org.eclipse.orion.server.tests.servlets.files.FileSystemTest;

/**
 * Core test:
 * - Start, stop a site
 * - Access workspace files through paths on the running site
 * - Access a remote URL through paths on the running site
 * 
 * Security test:
 * - Try to walk the workspace using ../ in hosted site path (should 404)
 * 
 * Concurrency test (done concurrently on many threads)
 * - Create a file with unique name 
 * - Create a new site that exposes the file
 * - Start the site
 * - Access the site, verify file content.
 * - Stop the site
 * - Attempt to access the file again, verify that request 404s (or times out?)
 */
public class HostingTest extends FileSystemTest {
	// TODO
}
