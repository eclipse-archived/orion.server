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
package org.eclipse.orion.server.cf.ext;

import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;

/**
 * Cloud foundry deployment extension service which allows to manipulate
 * the application environment. The service is activated after the
 * application is set up, i.e. created or updated.
 */
public interface ICFEnvironmentExtService {

	/**
	 * @param manifest The application manifest.
	 * @return <code>true</code> iff the service applies to the application
	 * 		represented by the given manifest.
	 */
	public boolean applies(ManifestParseTree manifest);

	/**
	 * @param target Cloud foundry target to which the application is being deployed to.
	 * @param app The application wrapper object.
	 * @return The resulting server status determining whether the deployment process
	 * 	should be continued or aborted.
	 */
	public ServerStatus apply(Target target, App app);
}
