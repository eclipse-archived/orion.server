/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/

function createUser() {
	dojo.byId('createUserForm').style.visibility = 'visible';
	dojo.byId('createUserFormMask').style.visibility = 'visible';
	dojo.byId('closeCreateUserForm').style.visibility = 'inherit';
	setTimeout(function() {
		dojo.byId('loginCreateUser').focus();
	}, 0);
};


function closeCreateUserForm() {
	dojo.byId('createUserForm').style.visibility = 'hidden';
	dojo.byId('createUserFormMask').style.visibility = 'hidden';
	dojo.byId('createUserErrorsList').innerHTML = ''
}

var userCreatedNotifier = function(userLogin, userPassword, userStore) {};
var userStore;

function confirmCreateUser() {
	var userLogin = dojo.byId("loginCreateUser").value;
	var userPassword = dojo.byId("passwordCreateUser").value;
	dojo.xhrPost({
		url : "/users/create",
		headers : {
			"Orion-Version" : "1"
		},
		content : {
			login : userLogin,
			password : userPassword,
			store: userStore,
			passwordConf : dojo.byId("passwordConfCreateUser").value
		},
		handleAs : "text",
		timeout : 15000,
		load : function(response, ioArgs) {
			userCreatedNotifier(userLogin, userPassword, userStore);
			dojo.byId('createUserForm').style.visibility = 'hidden';
			dojo.byId('createUserFormMask').style.visibility = 'hidden';
			dojo.byId('createUserErrorsList').innerHTML = '';
			return response;
		},
		error : function(response, ioArgs) {
			eval(ioArgs.xhr.responseText);
			return response;
		}
	});

}