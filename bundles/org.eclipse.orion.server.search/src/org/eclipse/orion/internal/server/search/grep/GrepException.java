package org.eclipse.orion.internal.server.search.grep;

public class GrepException extends Exception {

	private static final long serialVersionUID = 4387363119495722008L;

	public GrepException(String message) {
		super(message);
	}

	public GrepException(Throwable cause) {
		super(cause.getMessage(), cause);
	}
}