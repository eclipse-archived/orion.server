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
var userStore;

function setUserStore(userStoreToSet){
	if(userStore){
		document.getElementById('Login_'+userStore).style.color = '';
	}
	userStore = userStoreToSet;
	document.getElementById('Login_'+userStore).style.color = '#444';
	document.getElementById('store').value=userStore;
}

function confirmLogin() {
	/* handled by submit form */
}

function showCreateUser(){
	document.getElementById('newUserTable').style.display = '';
	document.getElementById('newUserHeader').style.display = 'none';
	setTimeout(function() {
		document.getElementById('create_login').focus();
	}, 0);
}
function validatePasswords(){
	if(document.forms["CreateUserForm"].password.value!==document.forms["CreateUserForm"].passwordRetype.value){
		alert("Passwords don't match!");
		document.getElementById("errorWin").style.display = '';
		document.getElementById("errorMessage").innerHTML = "Passwords don't match!";
		return false;
	}
	return true;
}