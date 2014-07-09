/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.node.objects;

import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONObject;

@ResourceDescription(type = PackageJSON.TYPE)
public class PackageJSON {

	public static final String TYPE = "PackageJSON";

	private JSONObject packageJSON;

	public PackageJSON(JSONObject packageJSON) {
		this.packageJSON = packageJSON;
	}

	public JSONObject getJSON() {
		return this.packageJSON;
	}

}
