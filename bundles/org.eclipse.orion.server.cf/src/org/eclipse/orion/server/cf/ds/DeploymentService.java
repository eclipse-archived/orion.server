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

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.server.cf.ds.objects.Plan;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;

public final class DeploymentService implements IDeploymentService {

	private List<IDeploymentPlanner> planners;

	protected synchronized void bind(IDeploymentPlanner planner) {
		planners.add(planner);
	}

	protected synchronized void unbind(IDeploymentPlanner planner) {
		planners.remove(planner);
	}

	public DeploymentService() {
		planners = new ArrayList<IDeploymentPlanner>();
	}

	@Override
	public String getDefaultDeplomentPlanner() {
		return GenericDeploymentPlanner.class.getSimpleName();
	}

	@Override
	public IDeploymentPlanner getDeploymentPlanner(String id) {
		if (id == null)
			return null;

		for (IDeploymentPlanner planner : planners)
			if (id.equals(planner.getId()))
				return planner;

		return null;
	}

	@Override
	public List<Plan> getDeploymentPlans(IFileStore contentLocation, ManifestParseTree manifest) {
		List<Plan> plans = new ArrayList<Plan>();
		for (IDeploymentPlanner planner : planners) {
			Plan plan = planner.getDeploymentPlan(contentLocation, manifest != null ? new ManifestParseTree(manifest) : null);
			if (plan != null)
				plans.add(plan);
		}

		return plans;
	}
}
