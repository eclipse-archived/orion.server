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

    packages: [
        {
	      name: 'dojo',
	      location: '../org.dojotoolkit/dojo',
	      main: 'lib/main-browser',
	      lib: '.'
	    },
	    {
	      name: 'dijit',
	      location: '../org.dojotoolkit/dijit',
	      main: 'lib/main',
	      lib: '.'
	    },
	    {
	      name: 'dojox',
	      location: '../org.dojotoolkit/dojox',
	      main: 'lib/main',
	      lib: '.'
	    }		    
	],
	  
    paths: {
    	orion: '../js',
	    text: '../requirejs/text',
	    i18n: '../requirejs/i18n'
    },
})