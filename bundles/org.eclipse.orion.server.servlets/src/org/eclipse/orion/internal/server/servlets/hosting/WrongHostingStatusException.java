package org.eclipse.orion.internal.server.servlets.hosting;

/**
 * Thrown when a request is made to perform some action on a hosted site, but the hosted
 * site is not in a state that permits the action.
 */
public class WrongHostingStatusException extends SiteHostingException {
	private static final long serialVersionUID = 1L;

	public WrongHostingStatusException(String msg) {
		super(msg);
	}
}
