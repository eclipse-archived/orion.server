/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/

var notify = false;
var userStore;

function login(error) {
	notify = false;
	dojo.byId('loginWindowMask').style.visibility = 'visible';
	dojo.byId('loginWindow').style.visibility = 'visible';
	dojo.byId('closeLoginWindow').style.visibility = 'inherit';
	setTimeout(function() {
		dojo.byId('login').focus();
	}, 0);
	if (error) {
		document.getElementById("errorWin").style.display = '';
		document.getElementById("errorMessage").innerHTML = error;
	} else {
		document.getElementById("errorWin").style.display = 'none';
	}
};

function setUserStore(userStoreToSet) {
	if (userStore) {
		if(document.getElementById('Login_' + userStore)){
			document.getElementById('Login_' + userStore).style.color = '';
		}
	}
	userStore = userStoreToSet;
	if(document.getElementById('Login_' + userStore)){
		document.getElementById('Login_' + userStore).style.color = '#444';
	}
}

function authDone() {
	authenticationInProgress = false;
	if (notify)
		dojo.publish("/auth", [ "auth done" ]);
};

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

function confirmLogin() {
	/* don't wait for the login response, notify anyway */
	notify = true;
	dojo
			.xhrPost({
				url : "/login",
				headers : {
					"Orion-Version" : "1"
				},
				content : {
					login : dojo.byId("login").value,
					password : dojo.byId("password").value,
					store : userStore
				},
				handleAs : "json",
				timeout : 15000,
				load : function(jsonData, ioArgs) {
					/*var statusPane = dojo.byId("authStatusPane")||null;
					if (statusPane!=null) {
						dojo.byId("authStatusPane").innerHTML = dojo
								.byId("login").value;
						document.getElementById("signOutUser").innerHTML = "Sign out";
						document.getElementById("signOutUser").onclick = logout;
					}*/
					checkUser();
					authDone();
					return jsonData;
				},
				error : handleLoginError
			});
	dojo.byId('loginWindow').style.visibility = 'hidden';
	dojo.byId('loginWindowMask').style.visibility = 'hidden';
};

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
		login("Internal error during authentication");
		break;
	case 401:
		login("Invalid user login");
	default:
		login(error.message);
	}

	return error;
}

function userCreated(username, password, store) {
	notify = true;
	dojo.xhrPost({
		url : "/login",
		headers : {
			"Orion-Version" : "1"
		},
		content : {
			login : username,
			password : password,
			store : store
		},
		handleAs : "json",
		timeout : 15000,
		load : function(jsonData, ioArgs) {
			/*var statusPane = dojo.byId("authStatusPane")||null;
			if (statusPane!=null) {
				dojo.byId("authStatusPane").innerHTML = username;
				document.getElementById("signOutUser").innerHTML = "Sign out";
				document.getElementById("signOutUser").onclick = logout;
			}*/
			checkUser();
			authDone();
			closeLoginWindow();
			return jsonData;
		},
		error : handleLoginError
	});
	dojo.byId('loginWindow').style.visibility = 'hidden';
	dojo.byId('loginWindowMask').style.visibility = 'hidden';
}

function checkUser() {
	/* don't wait for the login response, notify anyway */
	notify = true;
	dojo.xhrPost({
		url : "/login",
		headers : {
			"Orion-Version" : "1"
		},
		handleAs : "json",
		timeout : 15000,
		load : function(jsonData, ioArgs) {
			var statusPane = dojo.byId("authStatusPane")||null;
			if (statusPane!=null) {
				var lastLogin = "N/A";
				if (jsonData.lastlogintimestamp != null)
					lastLogin = dojo.date.locale.format(new Date(jsonData.lastlogintimestamp), {formatLength: "short"});
				dojo.byId("authStatusPane").innerHTML = jsonData.login + " signed in at: " + lastLogin;
				document.getElementById("signOutUser").innerHTML = "Sign out";
				document.getElementById("signOutUser").onclick = logout;
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
			"Orion-Version" : "1"
		},
		handleAs : "json",
		timeout : 15000,
		load : function(jsonData, ioArgs) {
			var statusPane = dojo.byId("authStatusPane")||null;
			if (statusPane!=null) {
				dojo.byId("authStatusPane").innerHTML = "--";
				document.getElementById("signOutUser").innerHTML = "Sign in";
				document.getElementById("signOutUser").onclick=login;
			}
			window.location.replace("/index.html");
			return jsonData;
		},
		error : function(response, ioArgs) {
			return response;
		}
	});
};

function showCreateUser() {
	document.getElementById('newUserTable').style.display = '';
	document.getElementById('newUserHeader').style.display = 'none';
	setTimeout(function() {
		dojo.byId('create_login').focus();
	}, 0);
}

function confirmCreateUser() {
	var userLogin = dojo.byId("create_login").value;
	var userPassword = dojo.byId("create_password").value;
	var userPasswordRetype = dojo.byId("create_passwordRetype").value;
	var userStore = dojo.byId("create_store").value;
	if (userPassword !== userPasswordRetype) {
		login("Passwords do not match.");
		showCreateUser();
		return;
	}
	dojo.xhrPost({
		url : "/users",
		headers : {
			"Orion-Version" : "1"
		},
		content : {
			login : userLogin,
			password : userPassword,
			store : userStore,
			passwordConf : userPasswordRetype
		},
		handleAs : "text",
		timeout : 15000,
		load : function(response, ioArgs) {
			userCreated(userLogin, userPassword, userStore);
			return response;
		},
		error : function(response, ioArgs) {
			if (ioArgs.xhr.responseText) {

				var tempDiv = document.createElement('div');
				tempDiv.innerHTML = ioArgs.xhr.responseText;
				tempDiv.childNodes;
				var error = tempDiv.getElementsByTagName("title")[0];
				if(error)
					login(error.text);
				else
					login("User could not be created.");
				showCreateUser();
			} else {
				login("User could not be created.");
				showCreateUser();
			}
			return response;
		}
	});

}