package org.eclipse.orion.internal.server.servlets.hosting;

/**
 * Thrown when an attempt is made to host a site configuration, but all domains and IPs
 * available for use as virtual host names have already been allocated.
 */
public class NoMoreHostsException extends SiteHostingException {
	private static final long serialVersionUID = 1L;

	public NoMoreHostsException(String msg) {
		super(msg);
	}

	public NoMoreHostsException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
