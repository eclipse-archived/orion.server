/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/

if (document.getElementById("openIdAuth") == null) {
	document.documentElement.getElementsByTagName("head")[0].appendChild(stylg);
	var scriptElement = document.createElement('script');
	if (dojo.isIE) {
		scriptElement.text = scr;
	} else {
		scriptElement.appendChild(document.createTextNode(scr));
	}
	scriptElement.type = 'text/javascript';
	scriptElement.id = 'openIdAuth';
	document.body.appendChild(divg);
	document.body.appendChild(scriptElement);
	dojo.byId('loginWindow').style.visibility = 'hidden';
	dojo.byId('loginWindowMask').style.visibility = 'hidden';

	// add the logout button to the toolbar if available
	var toolBar = dojo.byId('eclipseWebToolbar');
	if (toolBar) {
		var signout = document.createElement('span');
		signout.appendChild(document.createTextNode("Sign in"));
		signout.onclick = function() {
			login();
		};
		signout.id = "signOutUser";
		dojo.addClass(signout, "commandLink");
		toolBar.appendChild(signout);
		var bElement = document.createElement('b');
		bElement.setAttribute('id', 'authStatusPane');
		bElement.innerHTML = '--';
		if (dojo.isIE) {
			bElement.setAttribute('className', 'statuspane');
		} else {
			bElement.setAttribute('class', 'statuspane');
		}

		toolBar.appendChild(bElement);
	}
}
