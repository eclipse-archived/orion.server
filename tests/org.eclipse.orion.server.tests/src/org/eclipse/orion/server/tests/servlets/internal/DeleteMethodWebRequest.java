/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.servlets.internal;

import java.net.URL;

import com.meterware.httpunit.GetMethodWebRequest;

public class DeleteMethodWebRequest extends GetMethodWebRequest {

	public DeleteMethodWebRequest(String arg0) {
		super(arg0);
	}

	public DeleteMethodWebRequest(URL arg0, String arg1) {
		super(arg0, arg1);
	}

	public DeleteMethodWebRequest(URL arg0, String arg1, String arg2) {
		super(arg0, arg1, arg2);
	}

	@Override
	public String getMethod() {
		return "DELETE";
	}
}