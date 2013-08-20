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
import java.io.FilenameFilter;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.Path;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.RollingPolicyBase;
import ch.qos.logback.core.rolling.helper.FileNamePattern;

/**
 * Default ILogService implementation.
 */
public class LogService implements ILogService {

	@Override
	public List<Logger> getLoggers() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory
				.getILoggerFactory();
		return loggerContext.getLoggerList();
	}

	@Override
	public Logger getLogger(String name) {
		if (name == null)
			return null;

		LoggerContext loggerContext = (LoggerContext) LoggerFactory
				.getILoggerFactory();
		return loggerContext.getLogger(name);
	}

	@Override
	public List<FileAppender<ILoggingEvent>> getFileAppenders() {
		List<FileAppender<ILoggingEvent>> fileAppenders = new LinkedList<FileAppender<ILoggingEvent>>();

		for (Logger logger : getLoggers()) {
			for (Iterator<Appender<ILoggingEvent>> index = logger
					.iteratorForAppenders(); index.hasNext();) {

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

		for (Logger logger : getLoggers()) {
			for (Iterator<Appender<ILoggingEvent>> index = logger
					.iteratorForAppenders(); index.hasNext();) {

				Appender<ILoggingEvent> appender = index.next();
				if (appender instanceof FileAppender
						&& name.equals(appender.getName()))
					return (FileAppender<ILoggingEvent>) appender;
			}
		}

		return null;
	}

	@Override
	public RollingFileAppender<ILoggingEvent> getRollingFileAppender(String name) {
		FileAppender<ILoggingEvent> fileAppender = getFileAppender(name);
		if (fileAppender == null)
			return null;

		if (fileAppender instanceof RollingFileAppender<?>)
			return (RollingFileAppender<ILoggingEvent>) fileAppender;

		return null;
	}

	@Override
	public File[] getArchivedLogFiles(
			RollingFileAppender<ILoggingEvent> rollingFileAppender) {
		if (rollingFileAppender == null)
			return null;

		RollingPolicy rollingPolicy = rollingFileAppender.getRollingPolicy();
		if (rollingPolicy == null)
			return null;

		if (rollingPolicy instanceof RollingPolicyBase) {
			RollingPolicyBase policy = (RollingPolicyBase) rollingPolicy;
			String fileNamePattern = policy.getFileNamePattern();
			Context context = rollingFileAppender.getContext();

			File dir = null;
			FileNamePattern pattern = new FileNamePattern(fileNamePattern,
					context);
			Path path = new Path(pattern.toRegex(new Date()));

			if (!path.isAbsolute())
				dir = new File("."); //$NON-NLS-1$
			else {
				dir = path.removeLastSegments(1).toFile();

				/* extract relative pattern */
				Path p = new Path(fileNamePattern);
				pattern = new FileNamePattern(p.lastSegment(), context);
			}

			final String patternRegex = pattern.toRegex();
			File[] files = dir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.matches(patternRegex);
				}
			});

			return files;
		}

		return null;
	}

	@Override
	public File getArchivedLogFile(
			RollingFileAppender<ILoggingEvent> rollingFileAppender,
			String logFileName) {
		if (rollingFileAppender == null || logFileName == null)
			return null;

		File[] archvieLogFiles = getArchivedLogFiles(rollingFileAppender);
		if (archvieLogFiles == null)
			return null;

		for (File logFile : archvieLogFiles)
			if (logFileName.equals(logFile.getName()))
				return logFile;

		return null;
	}

	@Override
	public List<RollingFileAppender<ILoggingEvent>> getRollingFileAppenders() {
		List<RollingFileAppender<ILoggingEvent>> rollingFileAppenders = new LinkedList<RollingFileAppender<ILoggingEvent>>();

		for (Logger logger : getLoggers()) {
			for (Iterator<Appender<ILoggingEvent>> index = logger
					.iteratorForAppenders(); index.hasNext();) {

				Appender<ILoggingEvent> appender = index.next();
				if (appender instanceof RollingFileAppender)
					rollingFileAppenders
							.add((RollingFileAppender<ILoggingEvent>) appender);
			}
		}

		return rollingFileAppenders;
	}
}