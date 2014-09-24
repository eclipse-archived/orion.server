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
package org.eclipse.orion.server.git.objects;

/**
 * Helper class encapsulating a StashRef in both a numeric (0,1..) and string representation (e.g. stash@{0}).
 * 
 * @author mbendkowski
 *
 */
public final class StashRef {

	private int ref;

	public StashRef(int ref) {
		this.ref = ref;
	}

	public int getRef() {
		return ref;
	}

	public String getStringRef() {
		return String.format("stash@{%s}", ref); //$NON-NLS-1$
	}
}
