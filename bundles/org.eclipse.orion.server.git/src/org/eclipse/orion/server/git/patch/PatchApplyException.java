package org.eclipse.orion.server.git.patch;

public class PatchApplyException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * @param message
	 * @param cause
	 */
	public PatchApplyException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param message
	 */
	public PatchApplyException(String message) {
		super(message);
	}
}
