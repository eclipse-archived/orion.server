({
    optimizeCss: "standard.keepLines",

    closure: {
        CompilerOptions: {},
        CompilationLevel: 'SIMPLE_OPTIMIZATIONS',
        loggingLevel: 'WARNING'
    },

    pragmas: {
        asynchLoader: true
    },

    locale: 'en-us',
    inlineText: true,

    baseUrl: '.',

    // set the paths to our library packages
    packages: [{
        name: 'dojo',
        location: 'org.dojotoolkit/dojo',
        main: 'lib/main-browser',
        lib: '.'
    }, {
        name: 'dijit',
        location: 'org.dojotoolkit/dijit',
        main: 'lib/main',
        lib: '.'
    }, {
        name: 'dojox',
        location: 'org.dojotoolkit/dojox',
        main: 'lib/main',
        lib: '.'
    }],
    paths: {
        text: 'requirejs/text',
        i18n: 'requirejs/i18n',
	    domReady: 'requirejs/domReady'
    }
})