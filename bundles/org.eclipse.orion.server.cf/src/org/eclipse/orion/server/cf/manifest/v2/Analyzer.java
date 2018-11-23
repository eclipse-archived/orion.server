/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.manifest.v2;

public interface Analyzer {

	/**
	 * Applies an in-place analyzer to the manifest representation.
	 * @param node Intermediate manifest tree representation to be modified and/or analyzed.
	 * @throws AnalyzerException Whenever the manifest representation could not be modified
	 * 	or did not pass the analysis properly.
	 */
	public void apply(ManifestParseTree node) throws AnalyzerException;
}
