package org.eclipse.orion.server.git.patch;

import java.util.List;
import org.eclipse.jgit.patch.FormatError;

public class PatchFormatException extends Exception {
	private static final long serialVersionUID = 1L;
	private List<FormatError> errors;

	/**
	 * @param errors
	 */
	public PatchFormatException(List<FormatError> errors) {
		this.errors = errors;
	}

	/**
	* @return all the errors where unresolved conflicts have been detected
	  */
	public List<FormatError> getErrors() {
		return errors;
	}

}
