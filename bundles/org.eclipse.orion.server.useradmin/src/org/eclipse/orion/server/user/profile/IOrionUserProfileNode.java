/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.user.profile;

import org.eclipse.core.runtime.CoreException;

public interface IOrionUserProfileNode {

	public void put(String key, String value, boolean encrypt) throws CoreException;

	public String get(String key, String def) throws CoreException;

	public void remove(String key);
	
	public String[] keys();

	public IOrionUserProfileNode getUserProfileNode(String pathName);

	public boolean userProfileNodeExists(String pathName);

	public void removeUserProfileNode();
	
	public String[] childrenNames();

	public void flush() throws CoreException;
}
