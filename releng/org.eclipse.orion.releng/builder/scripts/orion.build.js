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
    paths: {
        text: 'requirejs/text',
        i18n: 'requirejs/i18n',
	    domReady: 'requirejs/domReady'
    }
})