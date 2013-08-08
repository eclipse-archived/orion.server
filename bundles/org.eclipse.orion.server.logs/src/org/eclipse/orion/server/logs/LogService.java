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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;

/**
 * Default ILogService implementation.
 */
public class LogService implements ILogService {

	@Override
	public List<FileAppender<ILoggingEvent>> getFileAppenders() {
		List<FileAppender<ILoggingEvent>> fileAppenders = new LinkedList<FileAppender<ILoggingEvent>>();
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

		for (Logger logger : loggerContext.getLoggerList()) {
			for (Iterator<Appender<ILoggingEvent>> index = logger.iteratorForAppenders(); index
					.hasNext();) {

				Appender<ILoggingEvent> appender = index.next();
				if (appender instanceof FileAppender)
					fileAppenders.add((FileAppender<ILoggingEvent>) appender);
			}
		}

		return fileAppenders;
	}

	@Override
	public FileAppender<ILoggingEvent> getFileAppender(String name) {
		if (name == null)
			return null;

		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

		for (Logger logger : loggerContext.getLoggerList()) {
			for (Iterator<Appender<ILoggingEvent>> index = logger.iteratorForAppenders(); index
					.hasNext();) {

				Appender<ILoggingEvent> appender = index.next();
				if (appender instanceof FileAppender && name.equals(appender.getName())) {
					return (FileAppender<ILoggingEvent>) appender;
				}
			}
		}

		return null;
	}
}
