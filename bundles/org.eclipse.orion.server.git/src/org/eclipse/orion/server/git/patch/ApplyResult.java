/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.patch;

import java.util.*;
import org.eclipse.jgit.patch.FormatError;

/**
 * Encapsulates the result of a {@link ApplyCommand}
 */
public class ApplyResult {

	private List<FormatError> formatErrors = Collections.emptyList();

	private List<ApplyError> applyErrors = new ArrayList<ApplyError>();

	/**
	 * @param formatErrors
	 *            formatting errors
	 * @return this instance
	 */
	public ApplyResult setFormatErrors(List<FormatError> formatErrors) {
		this.formatErrors = formatErrors;
		return this;
	}

	/**
	 * @return collection of formatting errors, if any.
	 */
	public List<FormatError> getFormatErrors() {
		return formatErrors;
	}

	/**
	 * @param applyError
	 *            an error that occurred when applying a patch
	 * @return this instance
	 */
	public ApplyResult addApplyError(ApplyError applyError) {
		applyErrors.add(applyError);
		return this;
	}

	/**
	 * @return collection of applying errors, if any.
	 */
	public List<ApplyError> getApplyErrors() {
		return applyErrors;
	}

}
