/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.internal.server.user.guest;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;

public class GuestUserProfileService implements IOrionUserProfileService {

	public IOrionUserProfileNode getUserProfileNode(String userName, String partId) {
		// TODO Auto-generated method stub
		return new IOrionUserProfileNode() {

			public boolean userProfileNodeExists(String pathName) {
				// TODO Auto-generated method stub
				return false;
			}

			public void removeUserProfileNode() {
				// TODO Auto-generated method stub

			}

			public void remove(String key) {
				// TODO Auto-generated method stub

			}

			public void put(String key, String value, boolean encrypt) throws CoreException {
				// TODO Auto-generated method stub

			}

			public String[] keys() {
				// TODO Auto-generated method stub
				return null;
			}

			public IOrionUserProfileNode getUserProfileNode(String pathName) {
				// TODO Auto-generated method stub
				return null;
			}

			public String get(String key, String def) throws CoreException {
				// TODO Auto-generated method stub
				return null;
			}

			public void flush() throws CoreException {
				// TODO Auto-generated method stub

			}

			public String[] childrenNames() {
				// TODO Auto-generated method stub
				return null;
			}
		};
	}

	public IOrionUserProfileNode getUserProfileNode(String userName, boolean create) {
		// TODO Auto-generated method stub
		return new IOrionUserProfileNode() {

			public boolean userProfileNodeExists(String pathName) {
				// TODO Auto-generated method stub
				return false;
			}

			public void removeUserProfileNode() {
				// TODO Auto-generated method stub

			}

			public void remove(String key) {
				// TODO Auto-generated method stub

			}

			public void put(String key, String value, boolean encrypt) throws CoreException {
				// TODO Auto-generated method stub

			}

			public String[] keys() {
				// TODO Auto-generated method stub
				return null;
			}

			public IOrionUserProfileNode getUserProfileNode(String pathName) {
				// TODO Auto-generated method stub
				return null;
			}

			public String get(String key, String def) throws CoreException {
				// TODO Auto-generated method stub
				return null;
			}

			public void flush() throws CoreException {
				// TODO Auto-generated method stub

			}

			public String[] childrenNames() {
				// TODO Auto-generated method stub
				return null;
			}
		};
	}

	public String[] getUserNames() {
		// TODO Auto-generated method stub
		return null;
	}

}
