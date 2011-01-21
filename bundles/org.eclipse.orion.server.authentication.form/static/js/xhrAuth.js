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
		dojo.byId('login').focus();
	}, 0);
	if(error){
		document.getElementById("errorWin").style.display='';
		document.getElementById("errorMessage").innerHTML=error;
	}else{
		document.getElementById("errorWin").style.display='none';
	}
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
		login("Internal error while authentication");
		break;
	case 401:
		login("Invalid user login");
	default:
		login(error.message);
	}

	return error;
}

function authDone() {
	authenticationInProgress = false;
	if (notify)
		dojo.publish("/auth", [ "auth done" ]);
};

function closeLoginWindow() {
	notify=false;
	authDone();
	dojo.byId('loginWindow').style.visibility = 'hidden';
	dojo.byId('loginWindowMask').style.visibility = 'hidden';
}

function confirmLogin() {
	/* don't wait for the login response, notify anyway */
	notify = true;
	dojo.xhrPost({
		url : "/login",
		headers : {
			"Orion-Version" : "1"
		},
		content : {
			login : dojo.byId("login").value,
			password : dojo.byId("password").value
		},
		handleAs : "json",
		timeout : 15000,
		load : function(jsonData, ioArgs) {
					if (dojo.byId("authStatusPane") !== null) {
						dojo.byId("authStatusPane").innerHTML = dojo
								.byId("login").value;
						dijit.byId("signOutUser").attr("label", "Sign Out");
						dijit.byId("signOutUser").attr("onClick", logout);
					}
			authDone();
			return jsonData;
		},
		error : handleLoginError
	});
	dojo.byId('loginWindow').style.visibility = 'hidden';
	dojo.byId('loginWindowMask').style.visibility = 'hidden';
};

function addUser(redirectVal){
	dojo.xhrGet({
		url : "/users/create",
		headers : {
			"Orion-Version" : "1"
		},
		handleAs : "text",
		content: {
			redirect: redirectVal,
			onUserCreated: "userCreated"
		},
		timeout : 15000,
		load : function(javascript, ioArgs) {
			dojo.byId('loginWindow').style.visibility = 'hidden';
			dojo.byId('loginWindowMask').style.visibility = 'hidden';
			eval(ioArgs.xhr.responseText);
		},
		error : function(response, ioArgs) {
			return response;
		}
	});
};

function userCreated(username, password){
	notify = true;
	dojo.xhrPost({
		url : "/login",
		headers : {
			"Orion-Version" : "1"
		},
		content : {
			login : username,
			password : password
		},
		handleAs : "json",
		timeout : 15000,
		load : function(jsonData, ioArgs) {
			if (dojo.byId("authStatusPane") !== null) {
				dojo.byId("authStatusPane").innerHTML = username;
				dijit.byId("signOutUser").attr("label", "Sign Out");
				dijit.byId("signOutUser").attr("onClick", logout);
			}
			authDone();
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
			"Orion-Version" : "1"
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