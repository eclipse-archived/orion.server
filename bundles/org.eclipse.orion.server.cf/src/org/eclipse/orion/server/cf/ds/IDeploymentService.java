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

import java.util.List;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.server.cf.ds.objects.Plan;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;

public interface IDeploymentService {

	public String getDefaultDeplomentPlannerId();

	public IDeploymentPlanner getDefaultDeplomentPlanner();

	public IDeploymentPlanner getDeploymentPlanner(String id);

	public List<Plan> getDeploymentPlans(IFileStore contentLocation, ManifestParseTree manifest);

	public String getDefaultDeplomentPackagerId();

	public IDeploymentPackager getDefaultDeplomentPackager();

	public IDeploymentPackager getDeploymentPackager(String id);
}
