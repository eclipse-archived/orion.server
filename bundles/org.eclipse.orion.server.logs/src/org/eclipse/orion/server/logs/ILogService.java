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

import java.util.List;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

/**
 * Provides the OSGI Log service definition interface. The service handles
 * file-based logback appenders visible from within the current Orion logger
 * context.
 */
public interface ILogService {

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
}
