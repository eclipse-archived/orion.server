/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
function openidLogin() {
	document.getElementById('openidLogin').style.display = '';
	document.getElementById('openidLink').style.display = 'none';
	setTimeout(function() {
		document.getElementById('openidSite').focus();
	}, 0);
}

function confirmOpenId() {
	/* don't wait for the login response, notify anyway */
	notify = true;
	var openid = document.getElementById('openidSite').value;
	if (openid != "" && openid != null) {
		window.location = "/login/openid?openid=" + encodeURIComponent(openid)
				+ "&redirect=" + window.location;
	}
};

function confirmLogin() {
	/* handled by submit form */
}