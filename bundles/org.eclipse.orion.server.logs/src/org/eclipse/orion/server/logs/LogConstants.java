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
 * Constants used by the log service
 */
public final class LogConstants {
	public static final String CONFIG_FILE_LOG_PROVIDER_ENABLED = "orion.logs.logProviderEnabled"; //$NON-NLS-1$

	public static final String CONFIG_FILE_LOG_BUFFER_SIZE = "orion.logs.logBufferSize"; //$NON-NLS-1$

	public static final String KEY_APPENDER_NAME = "Name"; //$NON-NLS-1$

	public static final String KEY_APPENDER_IS_APPEND = "IsAppend"; //$NON-NLS-1$

	public static final String KEY_APPENDER_IS_PRUDENT = "IsPrudent"; //$NON-NLS-1$

	public static final String KEY_APPENDER_IS_STARTED = "IsStarted"; //$NON-NLS-1$

	public static final String KEY_APPENDER_ROLLING_POLICY = "RollingPolicy"; //$NON-NLS-1$

	public static final String KEY_APPENDER_TRIGGERING_POLICY = "TriggeringPolicy"; //$NON-NLS-1$

	public static final String KEY_APPENDER_ARCHIVED_LOG_FILES = "ArchivedLogFiles"; //$NON-NLS-1$

	public static final String KEY_ROLLING_POLICY_FILE_NAME_PATTERN = "FileNamePattern"; //$NON-NLS-1$

	public static final String KEY_ROLLING_POLICY_MAX_HISTORY = "MaxHistory"; //$NON-NLS-1$

	public static final String KEY_ROLLING_POLICY_CLEAN_HISTORY_ON_START = "CleanHistoryOnStart"; //$NON-NLS-1$

	public static final String KEY_ROLLING_POLICY_COMPRESSION_MODE = "CompressionMode"; //$NON-NLS-1$

	public static final String KEY_ROLLING_POLICY_MIN_INDEX = "MinIndex"; //$NON-NLS-1$

	public static final String KEY_ROLLING_POLICY_MAX_INDEX = "MaxIndex"; //$NON-NLS-1$

	public static final String KEY_TRIGGERING_POLICY_MAX_FILE_SIZE = "MaxFileSize"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_PLAIN_TEXT = "text/plain"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_GZIP = "application/x-gzip"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_ZIP = "application/zip"; //$NON-NLS-1$

	public static final String KEY_LOGGER_LEVEL = "Level"; //$NON-NLS-1$

	public static final String KEY_LOGGER_EFFECTIVE_LEVEL = "EffectiveLevel"; //$NON-NLS-1$

	public static final String KEY_ROLLING_FILE_APPENDER_LOCATION = "RollingFileAppenderLocation"; //$NON-NLS-1$
}