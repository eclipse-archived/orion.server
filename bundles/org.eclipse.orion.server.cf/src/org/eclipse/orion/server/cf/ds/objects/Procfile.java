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
package org.eclipse.orion.server.cf.ds.objects;

import java.io.*;
import java.util.HashMap;

public class Procfile extends HashMap<String, String> {
	private static final long serialVersionUID = 1L;

	public static Procfile load(InputStream inputStream) throws IOException {

		Procfile procfile = new Procfile();
		BufferedReader reader = null;

		try {

			String line = null;
			reader = new BufferedReader(new InputStreamReader(inputStream));

			while ((line = reader.readLine()) != null) {

				if (line.isEmpty() || line.trim().isEmpty())
					continue;

				line = line.trim();
				String[] p = line.split(":", 2); //$NON-NLS-1$
				if (p.length == 2)
					procfile.put(p[0], p[1]);
			}

			return procfile;

		} finally {
			if (reader != null)
				reader.close();
		}
	}
}
