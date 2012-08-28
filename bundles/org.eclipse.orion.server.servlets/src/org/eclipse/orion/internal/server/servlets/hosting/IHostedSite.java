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
package org.eclipse.orion.internal.server.servlets.hosting;

import java.util.List;
import java.util.Map;

public interface IHostedSite {

	/**
	 * @return The id of the SiteConfiguration this hosted site was launched from.
	 */
	public String getSiteConfigurationId();

	/**
	 * @return Mappings defined by the site configuration that this hosted site was launched from.
	 */
	public Map<String, List<String>> getMappings();

	/**
	 * @return The id of the user who launched this site.
	 */
	public String getUserId();

	/**
	 * @return Workspace id that this site will use.
	 */
	public String getWorkspaceId();

	/**
	 * @return The hostname where this site is accessible.
	 */
	public String getHost();

	/**
	 * @return The URL of the Orion server where the hosted files can be edited.
	 */
	public String getEditServerUrl();

}
