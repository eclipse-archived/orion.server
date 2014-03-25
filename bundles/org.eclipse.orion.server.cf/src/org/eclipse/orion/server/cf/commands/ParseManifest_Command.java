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
package org.eclipse.orion.server.cf.commands;

import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.manifest.v2.*;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestConstants;
import org.eclipse.orion.server.cf.manifest.v2.utils.ManifestUtils;
import org.eclipse.orion.server.cf.objects.App;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseManifest_Command extends AbstractCFCommand {
	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$
	private String commandName;

	private App application;
	private IFileStore fileStore;

	public ParseManifest_Command(Target target, App app, IFileStore fileStore) {
		super(target);
		this.application = app;
		this.fileStore = fileStore;
		this.commandName = NLS.bind("Parse application manifest: {0}", fileStore.toURI());
	}

	@Override
	protected ServerStatus _doIt() {
		try {
			/* lookup the manifest description */
			IFileStore manifestStore = fileStore.getChild(ManifestConstants.MANIFEST_FILE_NAME);
			if (!manifestStore.fetchInfo().exists()) {
				String msg = "Failed to find application manifest. If you have one, please select it or the folder that contains it before deploying.";
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
			}

			/* parse the manifest */
			URI targetURI = URIUtil.toURI(target.getUrl());
			ManifestParseTree manifest = ManifestUtils.parse(manifestStore, targetURI.toString());
			ManifestParseTree app = manifest.get("applications").get(0); //$NON-NLS-1$

			/* optional */
			ManifestParseTree pathNode = app.getOpt(CFProtocolConstants.V2_KEY_PATH);
			String path = pathNode != null ? pathNode.getLabel() : ""; //$NON-NLS-1$

			if (path.isEmpty())
				path = "."; //$NON-NLS-1$

			try {
				IFileStore appStore = fileStore.getFileStore(new Path(path));

				if (!appStore.fetchInfo().exists()) {
					String msg = NLS.bind("Failed to find application content due to incorrect path parameter: {0}", appStore.toURI());
					return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, msg, null);
				}

				application.setAppStore(appStore);
				application.setManifest(manifest);

			} catch (Exception ex) {
				return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "Failed to locate application contents as specified in the manifest.", null);
			}

			return new ServerStatus(Status.OK_STATUS, HttpServletResponse.SC_OK);

		} catch (TokenizerException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (ParserException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (AnalyzerException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (InvalidAccessException e) {
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, e.getMessage(), null);
		} catch (Exception e) {
			String msg = NLS.bind("An error occured when performing operation {0}", commandName); //$NON-NLS-1$
			logger.error(msg, e);
			return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
		}
	}
}
