/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

public final class EncodingUtils {
	
	/**
	 * Encodes the string for HTML by escaping potential HTML characters, i.e.
	 *  &, <, >, ", '. Follows https://www.owasp.org/index.php/Cross-site_Scripting_%28XSS%29 guidelines.
	 * @param input String to be encoded for HTML.
	 * @return Encoded string.
	 */
	public static String encodeForHTML(String input){
		StringBuilder sb = new StringBuilder(input.length());
		int length = input.length();
		
		for(int idx = 0; idx < length; ++idx){
			char c = input.charAt(idx);
			switch(c){
				case '&':
					sb.append("&amp;");
					break;
				case '<':
					sb.append("&lt;");
					break;
				case '>':
					sb.append("&gt;");
					break;
				case '"':
					sb.append("&quot;");
					break;
				case '\'':
					sb.append("&#x27;");
					break;
				default:
					sb.append(c);
					break;
			}
		}
			
		return sb.toString();
	}
	
}
