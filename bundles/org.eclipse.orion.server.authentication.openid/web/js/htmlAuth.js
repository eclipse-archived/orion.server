/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

window.onload = function() {

	var error = getParam("error");
	if(error){
		var errorMessage = decodeBase64(error);
			document.getElementById("errorWin").style.display = '';
			document.getElementById("errorMessage").innerHTML = errorMessage;
		}
}


function openidLogin() {
	document.getElementById('openidLogin').style.display = '';
	document.getElementById('openidLink').style.display = 'none';
	setTimeout(function() {
		document.getElementById('openidSite').focus();
	}, 0);
};


function getParam(key){
	var regex = new RegExp('[\\?&]'+key+'=([^&#]*)');
	var results = regex.exec(window.location.href);
	if (results == null)
		return;
	return results[1];
}

function confirmOpenId(openid) {
	/* don't wait for the login response, notify anyway */
	notify = true;
	if (openid != "" && openid != null) {
		window.location = "../openid?openid=" + encodeURIComponent(openid) /*+ "&redirect=" + window.location*/ ;
	}
};

function decodeBase64(input) {
	 
	 var _keyStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
	    var output = "";
	    var chr1, chr2, chr3;
	    var enc1, enc2, enc3, enc4;
	    var i = 0;

	    input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");

	    while (i < input.length) {

	        enc1 = _keyStr.indexOf(input.charAt(i++));
	        enc2 = _keyStr.indexOf(input.charAt(i++));
	        enc3 = _keyStr.indexOf(input.charAt(i++));
	        enc4 = _keyStr.indexOf(input.charAt(i++));

	        chr1 = (enc1 << 2) | (enc2 >> 4);
	        chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
	        chr3 = ((enc3 & 3) << 6) | enc4;

	        output = output + String.fromCharCode(chr1);

	        if (enc3 != 64) {
	            output = output + String.fromCharCode(chr2);
	        }
	        if (enc4 != 64) {
	            output = output + String.fromCharCode(chr3);
	        }

	    }
	    output = output.replace(/\r\n/g,"\n");
	    var utftext = "";

	    for (var n = 0; n < output.length; n++) {

	        var c = output.charCodeAt(n);

	        if (c < 128) {
	            utftext += String.fromCharCode(c);
	        }
	        else if((c > 127) && (c < 2048)) {
	            utftext += String.fromCharCode((c >> 6) | 192);
	            utftext += String.fromCharCode((c & 63) | 128);
	        }
	        else {
	            utftext += String.fromCharCode((c >> 12) | 224);
	            utftext += String.fromCharCode(((c >> 6) & 63) | 128);
	            utftext += String.fromCharCode((c & 63) | 128);
	        }

	    }

	    return utftext;
}