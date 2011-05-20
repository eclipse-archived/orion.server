/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.orion.internal.server.ui.console;

import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.internal.console.ConsoleView;

@SuppressWarnings("restriction")
public class OrionConsoleView extends ConsoleView {
	public static final String ID = "org.eclipse.orion.server.console.view";

	public OrionConsoleView() {
		super();
	}

	protected void configureToolBar(IToolBarManager mgr) {
		super.configureToolBar(mgr);

		mgr.remove(IConsoleConstants.LAUNCH_GROUP);
		IContributionItem[] items = mgr.getItems();
		if (items.length >= 3) {
			mgr.remove(items[items.length - 1]);
			mgr.remove(items[items.length - 2]);
			mgr.remove(items[items.length - 3]);
		}
	}
}