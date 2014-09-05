package org.eclipse.orion.internal.server.hosting;

/**
 * Thrown when an invalid virtual hostname was generated.
 */
public class BadHostnameException extends SiteHostingException {
	private static final long serialVersionUID = 1L;

	public BadHostnameException(String msg) {
		super(msg);
	}

	public BadHostnameException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
