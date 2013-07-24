/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.server.logs;

/**
 * Constants used by the log provider
 */
public final class LogConstants {
	public static final String CONFIG_FILE_LOG_PROVIDER_ENABLED = "orion.logs.logProviderEnabled"; //$NON-NLS-1$

	public static final String CONFIG_FILE_LOG_BUFFER_SIZE = "orion.logs.logBufferSize"; //$NON-NLS-1$

	public static final String KEY_APPENDER_NAME = "Name"; //$NON-NLS-1$

	public static final String KEY_APPENDER_IS_APPEND = "IsAppend"; //$NON-NLS-1$

	public static final String KEY_APPENDER_IS_PRUDENT = "IsPrudent"; //$NON-NLS-1$

	public static final String KEY_APPENDER_IS_STARTED = "IsStarted"; //$NON-NLS-1$

	public static final String KEY_APPENDER_DOWNLOAD = "Download"; //$NON-NLS-1$

	public static final String KEY_APPENDER_DOWNLOAD_LOCATION = "DownloadLocation"; //$NON-NLS-1$
}
