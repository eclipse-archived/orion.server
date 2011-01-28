Running
	./eclipse
will start the http server on port 8080.  Open a browser on 
	http://localhost:8080
to get started.

When prompted to login you can create a new account from the link in the login dialog.

If you want to link to existing content on the server, set the system property "org.eclipse.orion.server.core.allowedPathPrefixes"
eg:
   ./eclipse -vmargs -Dorg.eclipse.orion.server.core.allowedPathPrefixes=/server/path/prefix
And then use the Link Folder action in the toolbar in the browser UI.

To shutdown the server, type "close" in the server console.