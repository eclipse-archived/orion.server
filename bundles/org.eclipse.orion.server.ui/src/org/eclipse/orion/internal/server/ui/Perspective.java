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

package org.eclipse.orion.internal.server.ui;

import org.eclipse.orion.internal.server.ui.console.OrionConsoleView;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class Perspective implements IPerspectiveFactory {

	public void createInitialLayout(IPageLayout layout) {
		layout.addStandaloneView(OrionConsoleView.ID, true, IPageLayout.LEFT, 0.5f, "org.eclipse.ui.editorss");
		layout.setEditorAreaVisible(false);
		layout.setFixed(true);
	}
}
