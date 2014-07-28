package org.eclipse.orion.server.authentication.oauth;

public class OAuthException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5063237215282170865L;

	public OAuthException(String message) {
		super(message);
	}

	public OAuthException(Throwable cause) {
		super(cause.getMessage(), cause);
	}

}
