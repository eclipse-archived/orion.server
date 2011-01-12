Running
	./eclipse
will start the http server on port 8080.  Open a browser on 
	http://localhost:8080
to get started.

When prompted to login you can create a new account from the link in the login dialog.

If you want to link to existing content on the server, set the system property "org.eclipse.e4.webide.server.allowedPathPrefixes"
eg:
   ./eclipse -vmargs -Dorg.eclipse.e4.webide.server.allowedPathPrefixes=/server/path/prefix
And then use the Link Folder/Project action in the toolbar.

To shutdown the server, type "close" in the server console.