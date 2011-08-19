/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/

function confirmOpenId(openid) {
	/* don't wait for the login response, notify anyway */
	notify = true;
	if (openid != "" && openid != null) {
		window.location = "/login/openid?openid=" + encodeURIComponent(openid);
	}
};

function confirmLogin() {
	var mypostrequest = new XMLHttpRequest();
	mypostrequest.onreadystatechange = function() {
		if (mypostrequest.readyState == 4) {
			if (mypostrequest.status != 200
					&& window.location.href.indexOf("http") != -1) {
				responseObject = JSON.parse(mypostrequest.responseText);
				document.getElementById("errorMessage").innerHTML = responseObject.error;
				document.getElementById("errorWin").style.display = '';
			} else {
				localStorage.setItem('FORMOpenIdUser', mypostrequest.responseText);
				window.close();
			}
		}
	};
	var login = encodeURIComponent(document.getElementById("login").value)
	var password = encodeURIComponent(document.getElementById("password").value)
	var parameters = "login=" + login + "&password=" + password;
	mypostrequest.open("POST", "/login/form", true);
	mypostrequest.setRequestHeader("Content-type",
			"application/x-www-form-urlencoded");
	mypostrequest.setRequestHeader("Orion-Version", "1");
	mypostrequest.send(parameters);
}

function confirmCreateUser() {
	var mypostrequest = new XMLHttpRequest();
	mypostrequest.onreadystatechange = function() {
		if (mypostrequest.readyState == 4) {
			if (mypostrequest.status != 200
					&& window.location.href.indexOf("http") != -1) {
				responseObject = JSON.parse(mypostrequest.responseText);
				document.getElementById("errorMessage").innerHTML = responseObject.Message;
				document.getElementById("errorWin").style.display = '';
			} else {
				confirmLogin();
			}
		}
	};
	var login = encodeURIComponent(document.getElementById("login").value)
	var password = encodeURIComponent(document.getElementById("password").value)
	var parameters = "login=" + login + "&password=" + password;
	mypostrequest.open("POST", "/users", true);
	mypostrequest.setRequestHeader("Content-type",
			"application/x-www-form-urlencoded");
	mypostrequest.setRequestHeader("Orion-Version", "1");
	mypostrequest.send(parameters);
}

function validatePassword() {
	if (document.getElementById("password").value !== document.getElementById("passwordRetype").value) {
		document.getElementById("errorWin").style.display = '';
		document.getElementById("errorMessage").innerHTML = "Passwords don't match!";
		return false;
	}
	document.getElementById("errorWin").style.display = 'none';
	document.getElementById("errorMessage").innerHTML = "";
	return true;
}