package org.eclipse.orion.internal.server.servlets.hosting;

/**
 * Superclass for exceptions that may occur when dealing with hosted sites.
 */
public class SiteHostingException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public SiteHostingException(String msg) {
		super(msg);
	}
}
