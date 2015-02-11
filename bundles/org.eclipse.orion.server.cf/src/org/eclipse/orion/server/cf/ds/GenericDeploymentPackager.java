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
package org.eclipse.orion.server.cf.ds;

import java.io.File;
import java.io.IOException;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.utils.PackageUtils;

public class GenericDeploymentPackager implements IDeploymentPackager {

	@Override
	public String getId() {
		return getClass().getCanonicalName();
	}

	@Override
	public File getDeploymentPackage(App application, IFileStore contentLocation, String command) throws IOException, CoreException {
		return PackageUtils.getApplicationPackage(contentLocation);
	}
}
