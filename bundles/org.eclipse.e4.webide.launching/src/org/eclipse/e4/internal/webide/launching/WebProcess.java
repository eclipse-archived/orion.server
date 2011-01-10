/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.internal.webide.launching;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.osgi.service.http.HttpService;

public class WebProcess extends PlatformObject implements IProcess {
	public static final String WEB_PROCESS_ALIAS = "alias"; //$NON-NLS-1$

	private boolean terminated = false;
	private ILaunch launch = null;
	private Map<String, String> attributes;
	private Map<String, ProjectEntryHttpContext> contextMap;

	public WebProcess(ILaunch launch, Map<String, ProjectEntryHttpContext> contextMap) {
		this.launch = launch;
		this.contextMap = contextMap;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ITerminate#canTerminate()
	 */
	public boolean canTerminate() {
		return !isTerminated();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ITerminate#isTerminated()
	 */
	public boolean isTerminated() {
		return terminated;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ITerminate#terminate()
	 */
	public void terminate() throws DebugException {
		terminated = true;

		HttpService service = Activator.getHttpService();
		for (String projectAlias : contextMap.keySet()) {
			try {
				service.unregister(projectAlias);
			} catch (IllegalArgumentException e) {
				//already down?
			}
		}

		getLaunch().removeProcess(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IProcess#getLabel()
	 */
	public String getLabel() {
		// TODO Auto-generated method stub
		return "Web Process"; //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IProcess#getLaunch()
	 */
	public ILaunch getLaunch() {
		return launch;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IProcess#getStreamsProxy()
	 */
	public IStreamsProxy getStreamsProxy() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IProcess#setAttribute(java.lang.String, java.lang.String)
	 */
	public void setAttribute(String key, String value) {
		if (attributes == null)
			attributes = new HashMap<String, String>();
		attributes.put(key, value);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IProcess#getAttribute(java.lang.String)
	 */
	public String getAttribute(String key) {
		if (attributes == null)
			return null;
		return attributes.get(key);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IProcess#getExitValue()
	 */
	public int getExitValue() throws DebugException {
		return 0;
	}

	public String getHostedPath(IPath path) {
		for (String contextAlias : contextMap.keySet()) {
			ProjectEntryHttpContext context = contextMap.get(contextAlias);

			String hostedPath = context.getHostedPath(path);
			if (hostedPath != null) {
				return contextAlias + '/' + hostedPath;
			}
		}
		return null;
	}

}
