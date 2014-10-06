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
package org.eclipse.orion.server.cf.node.handlers.v1;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.orion.internal.server.servlets.ServletResourceHandler;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.cf.commands.ComputeTargetCommand;
import org.eclipse.orion.server.cf.commands.ParseManifestCommand;
import org.eclipse.orion.server.cf.jobs.CFJob;
import org.eclipse.orion.server.cf.manifest.v2.ManifestParseTree;
import org.eclipse.orion.server.cf.node.commands.*;
import org.eclipse.orion.server.cf.node.objects.Debug;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.cf.servlets.AbstractRESTHandler;
import org.eclipse.orion.server.core.*;
import org.eclipse.osgi.util.NLS;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugHandlerV1 extends AbstractRESTHandler<Debug> {

	private final Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.cf"); //$NON-NLS-1$
	private ManifestParseTree appManifest;
	private IFileStore appStore;

	public DebugHandlerV1(ServletResourceHandler<IStatus> statusHandler) {
		super(statusHandler);
	}

	@Override
	protected Debug buildResource(HttpServletRequest request, String path) throws CoreException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected CFJob handleGet(Debug resource, HttpServletRequest request, HttpServletResponse response, final String path) {
		final String contentLocation = extractDecodedContentLocation(request);
		final JSONObject targetJSON = extractJSONData(IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_TARGET));

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					if (contentLocation == null)
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "A ContentLocation must be provided", null);

					ComputeTargetCommand computeTarget = new ComputeTargetCommand(this.userId, targetJSON);
					IStatus result = computeTarget.doIt();
					if (!result.isOK())
						return result;
					Target target = computeTarget.getTarget();

					// Create an App
					result = DebugHandlerV1.this.checkApp(target, this.userId, contentLocation);
					if (!result.isOK())
						return result;

					GetAppInstrumentationStateCommand getState = new GetAppInstrumentationStateCommand(target, appStore);
					return getState.doIt();
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", path); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	private String extractDecodedContentLocation(HttpServletRequest request) {
		final String encodedContentLocation = IOUtilities.getQueryParameter(request, ProtocolConstants.KEY_CONTENT_LOCATION);
		String contentLocation = null;
		if (encodedContentLocation != null) {
			try {
				contentLocation = ServletResourceHandler.toOrionLocation(request, URLDecoder.decode(encodedContentLocation, "UTF8"));
			} catch (UnsupportedEncodingException e) {
				// do nothing
			}
		}
		return contentLocation;
	}

	// Obtains the app store and manifest, also checking user permission to access the content location
	private IStatus checkApp(Target target, String userId, String contentLocation) {
		// Parse the application manifest
		ParseManifestCommand parseManifestCommand = null;
		parseManifestCommand = new ParseManifestCommand(target, userId, contentLocation);
		IStatus status = parseManifestCommand.doIt();
		if (!status.isOK())
			return status;

		appManifest = parseManifestCommand.getManifest();
		appStore = parseManifestCommand.getAppStore();
		return new ServerStatus(IStatus.OK, HttpServletResponse.SC_OK, null, null, null);
	}

	@Override
	protected CFJob handlePut(Debug resource, HttpServletRequest request, HttpServletResponse response, final String pathString) {
		final JSONObject jsonData = extractJSONData(request);
		final JSONObject targetJSON = jsonData.optJSONObject(CFProtocolConstants.KEY_TARGET);

		final String password = jsonData.optString(CFProtocolConstants.KEY_PASSWORD);
		final String urlPrefix = jsonData.optString(CFProtocolConstants.KEY_URL_PREFIX);
		final String contentLocation = ServletResourceHandler.toOrionLocation(request, jsonData.optString(CFProtocolConstants.KEY_CONTENT_LOCATION, null));

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					if (contentLocation == null)
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "A ContentLocation must be provided", null);

					ComputeTargetCommand computeTarget = new ComputeTargetCommand(this.userId, targetJSON);
					IStatus status = computeTarget.doIt();
					if (!status.isOK())
						return status;
					Target target = computeTarget.getTarget();

					status = checkApp(target, this.userId, contentLocation);
					if (!status.isOK())
						return status;

					InstrumentNodeAppCommand instrument = new InstrumentNodeAppCommand(target, appStore, appManifest, password, urlPrefix);
					return instrument.doIt();
				} catch (Exception e) {
					String msg = "Failed to handle request"; //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}

	@Override
	protected CFJob handleDelete(Debug resource, HttpServletRequest request, HttpServletResponse response, final String pathString) {
		final String contentLocation = extractDecodedContentLocation(request);
		final JSONObject targetJSON = extractJSONData(IOUtilities.getQueryParameter(request, CFProtocolConstants.KEY_TARGET));

		return new CFJob(request, false) {
			@Override
			protected IStatus performJob() {
				try {
					if (contentLocation == null)
						return new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_BAD_REQUEST, "A ContentLocation must be provided", null);

					ComputeTargetCommand computeTarget = new ComputeTargetCommand(this.userId, targetJSON);
					IStatus status = computeTarget.doIt();
					if (!status.isOK())
						return status;
					Target target = computeTarget.getTarget();

					status = checkApp(target, this.userId, contentLocation);
					if (!status.isOK())
						return status;

					UninstrumentNodeAppCommand uninstrument = new UninstrumentNodeAppCommand(target, appStore, appManifest);
					return uninstrument.doIt();
				} catch (Exception e) {
					String msg = NLS.bind("Failed to handle request for {0}", pathString); //$NON-NLS-1$
					ServerStatus status = new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, e);
					logger.error(msg, e);
					return status;
				}
			}
		};
	}
}
