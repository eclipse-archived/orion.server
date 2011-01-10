/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/

if (document.getElementById("createUserForm") == null) {
	document.documentElement.getElementsByTagName("head")[0].appendChild(stylf);
	var loginFormScript = document.createElement('script');
	if (dojo.isIE) {
		loginFormScript.text = scrf;
	} else {
		loginFormScript.appendChild(document.createTextNode(scrf));
	}
	loginFormScript.type = 'text/javascript';
	loginFormScript.id = 'createUserForm';
	document.body.appendChild(divf);
	document.body.appendChild(loginFormScript);
	dojo.byId('createUserForm').style.visibility = 'hidden';
	dojo.byId('createUserFormMask').style.visibility = 'hidden';
}
createUser();