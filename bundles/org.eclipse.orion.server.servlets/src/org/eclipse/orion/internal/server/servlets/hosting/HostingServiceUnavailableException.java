package org.eclipse.orion.internal.server.servlets.hosting;

public class HostingServiceUnavailableException extends Exception {
	private static final long serialVersionUID = 1L;

	public HostingServiceUnavailableException(String message) {
		super(message);
	}
}
