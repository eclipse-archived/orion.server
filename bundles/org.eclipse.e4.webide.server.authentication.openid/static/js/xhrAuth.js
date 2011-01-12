/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/

var notify = false;

function login(error) {
	notify = false;
	dojo.byId('loginWindowMask').style.visibility = 'visible';
	dojo.byId('loginWindow').style.visibility = 'visible';
	dojo.byId('closeLoginWindow').style.visibility = 'inherit';
	setTimeout(function() {
		dojo.byId('openidSite').focus();
	}, 0);
	if (error) {
		document.getElementById("errorWin").style.display = '';
		document.getElementById("errorMessage").innerHTML = error;
	} else {
		document.getElementById("errorWin").style.display = 'none';
	}
};

function confirmOpenId() {
	/* don't wait for the login response, notify anyway */
	notify = true;
	var openid = dojo.byId('openidSite').value;
	if (openid != "" && openid != null) {
		win = window.open("/openid?openid=" + encodeURIComponent(openid),
				"openid_popup", "width=790,height=580");
		setTimeout("checkPopup(win)", 3000);
	}

	dojo.byId('loginWindow').style.visibility = 'hidden';
	dojo.byId('loginWindowMask').style.visibility = 'hidden';
};

function authDone() {
	authenticationInProgress = false;
	if (notify)
		dojo.publish("/auth", [ "auth done" ]);
};

function handleOpenIDResponse(openid_args, error) {
	/*
	 * TODO: receive the message in the main window with
	 * window.onEclipseMessage(...), see
	 * /org.eclipse.orion.client.core/static/js/message.js
	 */
	if (error) {
		login(error);
	} else {
		authDone();
		checkUser();
	}
}

function checkPopup(win) {
	if (!win.closed) {
		/*
		 * FIXME: if a request is made after the popup has been closed but the
		 * authenticationInProgress hasn't been cleared (no check made yet) the
		 * 401 response for the call will be ignored, when it should result in
		 * another auth prompt
		 */
		setTimeout("checkPopup(win)", 1000);
		/* periodically check if the popup window is still open */
	} else {
		authDone();
	}
}

function closeLoginWindow() {
	notify = false;
	authDone();
	dojo.byId('loginWindow').style.visibility = 'hidden';
	dojo.byId('loginWindowMask').style.visibility = 'hidden';
}

function handleLoginError(error, ioArgs) {
	try {
		if (JSON.parse(error.responseText).error) {
			login(JSON.parse(error.responseText).error);
			return error;
		}
	} catch (e) {
	}
	switch (ioArgs.xhr.status) {
	case 404:
		login("Cannot obtain login page");
		break;
	case 500:
		login("Internal error while authentication");
		break;
	case 401:
		login("Invalid user login");
	default:
		login(error.message);
	}

	return error;
}

function checkUser() {
	/* don't wait for the login response, notify anyway */
	notify = true;
	dojo.xhrPost({
		url : "/openid",
		headers : {
			"EclipseWeb-Version" : "1"
		},
		handleAs : "json",
		timeout : 15000,
		load : function(jsonData, ioArgs) {
			if (dojo.byId("authStatusPane") !== null) {
				dojo.byId("authStatusPane").innerHTML = jsonData.login;
				dijit.byId("signOutUser").attr("label", "Sign Out");
				dijit.byId("signOutUser").attr("onClick", logout);
			}
			return jsonData;
		},
		error : function(response, ioArgs) {
			return response;
		}
	});
};

function logout() {
	/* don't wait for the login response, notify anyway */
	notify = true;
	dojo.xhrPost({
		url : "/logout",
		headers : {
			"EclipseWeb-Version" : "1"
		},
		handleAs : "json",
		timeout : 15000,
		load : function(jsonData, ioArgs) {
			if (dojo.byId("authStatusPane") !== null) {
				dojo.byId("authStatusPane").innerHTML = "--";
				dijit.byId("signOutUser").attr("label", "Sign In");
				dijit.byId("signOutUser").attr("onClick", login);
			}
			window.location.replace("/index.html");
			return jsonData;
		},
		error : function(response, ioArgs) {
			return response;
		}
	});
};