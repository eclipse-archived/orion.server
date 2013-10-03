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
package org.eclipse.orion.internal.server.useradmin.simple;

import java.io.File;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.user.profile.IOrionUserProfileConstants;
import org.eclipse.orion.server.user.profile.IOrionUserProfileNode;
import org.eclipse.orion.server.user.profile.IOrionUserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the Orion user profile service on top of the simple meta store.
 * The meta data for each user is stored in the user.json in the simple meta store.
 * 
 * @author Anthony Hunter
 */
public class SimpleUserProfileService implements IOrionUserProfileService {

	private IOrionUserProfileNode root = null;

	public SimpleUserProfileService() throws CoreException {
		super();
		Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
		logger.debug("Started simple user profile service."); //$NON-NLS-1$
		IFileStore fileStore = OrionConfiguration.getUserHome(null);
		File rootLocation = fileStore.toLocalFile(EFS.NONE, null);
		root = new SimpleUserProfileRoot(rootLocation);
	}

	public IOrionUserProfileNode getUserProfileNode(String userName, String partId) {
		if (partId.equals(IOrionUserProfileConstants.GENERAL_PROFILE_PART)) {
			return root.getUserProfileNode(userName);
		}
		return null;
	}

	public IOrionUserProfileNode getUserProfileNode(String userName, boolean create) {
		if (create || root.userProfileNodeExists(userName)) {
			return root.getUserProfileNode(userName);
		}
		return null;
	}

	public String[] getUserNames() {
		return root.childrenNames();
	}

}
