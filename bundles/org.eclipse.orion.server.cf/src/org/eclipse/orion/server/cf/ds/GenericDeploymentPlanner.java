/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.ds;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.Path;
import org.eclipse.orion.server.cf.ds.objects.Plan;
import org.eclipse.orion.server.cf.ds.objects.Procfile;
import org.eclipse.orion.server.cf.manifest.v2.InvalidAccessException;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.core.IOUtilities;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GenericDeploymentPlanner implements IDeploymentPlanner {
	protected final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$
	public static String TYPE = "generic"; //$NON-NLS-1$

	@Override
	public String getId() {
		return getClass().getCanonicalName();
	}

	@Override
	public String getWizardId() {
		return "org.eclipse.orion.client.cf.wizard.generic"; //$NON-NLS-1$
	}

	protected String getApplicationName(IFileStore contentLocation) throws UnsupportedEncodingException {
		
		IFileStore rootStore = OrionConfiguration.getRootLocation();
		Path relativePath = new Path(URLDecoder.decode(contentLocation.toURI().toString(), "UTF8").substring(rootStore.toURI().toString().length()));
		if (relativePath.segmentCount() < 4) {
			// not a change to a file in a project
			return null;
		}
		
		String projectDirectory = relativePath.segment(3);
		projectDirectory = projectDirectory.replaceFirst(" \\| ", " --- ");
		String[] folderNameParts = projectDirectory.split(" --- ", 2);
		if (folderNameParts.length > 1)
			return folderNameParts[1];
		return folderNameParts[0];
	}

	protected String getApplicationHost(IFileStore contentLocation) throws UnsupportedEncodingException {

		IFileStore rootStore = OrionConfiguration.getRootLocation();
		Path relativePath = new Path(URLDecoder.decode(contentLocation.toURI().toString(), "UTF8").substring(rootStore.toURI().toString().length()));
		if (relativePath.segmentCount() < 4) {
			// not a change to a file in a project
			return null;
		}
		
		String folderName = relativePath.segment(3);
		folderName = folderName.replaceFirst(" \\| ", " --- ");
		return ManifestUtils.slugify(folderName);
	}

	private void set(ManifestParseTree application, String property, String defaultValue) {
		if (application.has(property))
			return;
		else
			application.put(property, defaultValue);
	}

	/**
	 * Looks for a Procfile and parses the web command.
	 * @return <code>null</code> iff there is no Procfile present or it does not contain a web command.
	 */
	private String getProcfileCommand(IFileStore contentLocation) {
		IFileStore procfileStore = contentLocation.getChild(ManifestConstants.PROCFILE);
		if (!procfileStore.fetchInfo().exists())
			return null;

		InputStream is = null;
		try {

			is = procfileStore.openInputStream(EFS.NONE, null);
			Procfile procfile = Procfile.load(is);
			return procfile.get(ManifestConstants.WEB);

		} catch (Exception ex) {
			/* can't parse the file, fail */
			return null;

		} finally {
			IOUtilities.safeClose(is);
		}
	}

	@Override
	public Plan getDeploymentPlan(IFileStore contentLocation, ManifestParseTree manifest, IFileStore manifestStore) {

		IFileStore appStore = contentLocation;
		try {
			if (manifest != null) {
				ManifestParseTree application = manifest.get(ManifestConstants.APPLICATIONS).get(0);
				if (application.has(ManifestConstants.PATH)) {
					appStore = contentLocation.getFileStore(new Path(application.get(ManifestConstants.PATH).getValue()));
				}
			}
		} catch (InvalidAccessException e) {
			logger.error("Problem while reading manifest", e);
		}

		try {
			String applicationName = getApplicationName(contentLocation);
			String manifestPath;

			if (manifest == null) {
				manifest = ManifestUtils.createBoilerplate(applicationName);
				manifestPath = null;
			} else {
				manifestPath = contentLocation.toURI().relativize(manifestStore.toURI()).toString();
			}

			/* set up generic defaults */
			ManifestParseTree application = manifest.get(ManifestConstants.APPLICATIONS).get(0);
			set(application, ManifestConstants.HOST, getApplicationHost(contentLocation));
			set(application, ManifestConstants.MEMORY, ManifestUtils.DEFAULT_MEMORY);
			set(application, ManifestConstants.INSTANCES, ManifestUtils.DEFAULT_INSTANCES);
			set(application, ManifestConstants.PATH, ManifestUtils.DEFAULT_PATH);

			String procfileCommand = getProcfileCommand(appStore);
			if (procfileCommand != null)
				set(application, ManifestConstants.COMMAND, procfileCommand);

			return new Plan(getId(), getWizardId(), TYPE, manifest, manifestPath);

		} catch (Exception ex) {
			/* Nobody expected the Spanish inquisition */
			String msg = NLS.bind("Failed to handle generic deployment plan for {0}", contentLocation.toString()); //$NON-NLS-1$
			logger.error(msg, ex);
			return null;
		}
	}
}
