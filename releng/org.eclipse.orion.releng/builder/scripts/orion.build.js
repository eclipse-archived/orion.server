/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials are made 
 * available under the terms of the Eclipse Public License v1.0 
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution 
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html). 
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

({
    optimizeCss: "standard.keepLines",

    closure: {
        CompilerOptions: {
            languageIn: Packages.com.google.javascript.jscomp.CompilerOptions.LanguageMode.fromString("ECMASCRIPT5")
        },
        CompilationLevel: 'SIMPLE_OPTIMIZATIONS',
        loggingLevel: 'WARNING'
    },

    pragmas: {
        asynchLoader: true
    },

    locale: 'en-us',
    inlineText: true,
    baseUrl: '.',
    paths: {
        text: 'requirejs/text',
        i18n: 'requirejs/i18n',
	    domReady: 'requirejs/domReady'
    }
})