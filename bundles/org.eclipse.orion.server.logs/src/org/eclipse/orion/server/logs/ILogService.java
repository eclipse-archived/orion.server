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

import java.io.File;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;

/**
 * Provides the OSGI Log service definition interface. The service handles
 * file-based logback appenders visible from within the current Orion logger
 * context.
 */
public interface ILogService {

	/**
	 * @return All Loggers in the current context.
	 */
	public List<Logger> getLoggers();

	/**
	 * @param name
	 *            Logback Logger name property.
	 * @return Appropriate Logger or <code>null</code> if not present in the
	 *         current logger context.
	 */
	public Logger getLogger(String name);

	/**
	 * @return All FileAppenders in the current context.
	 */
	public List<FileAppender<ILoggingEvent>> getFileAppenders();

	/**
	 * @param name
	 *            Logback FileAppender name property.
	 * @return Appropriate FileAppender or <code>null</code> if not present in
	 *         the current logger context.
	 */
	public FileAppender<ILoggingEvent> getFileAppender(String name);

	/**
	 * @return All RollingFileAppenders in the current context.
	 */
	public List<RollingFileAppender<ILoggingEvent>> getRollingFileAppenders();

	/**
	 * @param name
	 *            Logback RollingFileAppender name property.
	 * @return Appropriate RollingFileAppender or <code>null</code> if not
	 *         present in the current logger context.
	 */
	public RollingFileAppender<ILoggingEvent> getRollingFileAppender(String name);

	/**
	 * @param rollingFileAppender
	 *            The rolling file appender which log files are wished to be
	 *            retrieved.
	 * @return Appropriate File[] or <code>null</code> if none present in the
	 *         current logger context. Note <code>null</code> might be returned
	 *         also in case if the rollingFileAppender has no rolling policy
	 *         defined.
	 */
	public File[] getArchivedLogFiles(
			RollingFileAppender<ILoggingEvent> rollingFileAppender);

	/**
	 * @param rollingFileAppender
	 *            The rolling file appender which is responsible for populating
	 *            the searched log file.
	 * @param logFileName
	 *            The required log file name.
	 * @return Appropriate File or <code>null</code> if not present in the
	 *         current logger context.
	 */
	public File getArchivedLogFile(
			RollingFileAppender<ILoggingEvent> rollingFileAppender,
			String logFileName);
}