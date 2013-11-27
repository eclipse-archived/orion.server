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
package org.eclipse.orion.server.cf.utils;

import java.util.*;
import org.eclipse.orion.server.cf.objects.Target;

public class TargetMap {
	private Map<String, Target> targetMap;

	public TargetMap() {
		this.targetMap = Collections.synchronizedMap(new HashMap<String, Target>());
	}

	public Target getTarget(String user) {
		return this.targetMap.get(user);
	}

	public void putTarget(String user, Target target) {
		this.targetMap.put(user, target);
	}
}
