grammar ResourceShape;

options {
	output=AST;
	ASTLabelType=CommonTree;
}

tokens {
	PROPERTIES = 'properties';
	NESTED_PROPERTIES = 'nested_property';
}

@lexer::header {
package org.eclipse.orion.server.core.resources;
}

@lexer::members {
@Override
public void reportError(RecognitionException e) {
	throw new IllegalArgumentException(e);
}
}

@parser::header {
package org.eclipse.orion.server.core.resources;
}

@parser::members {
@Override
public void reportError(RecognitionException e) {
	throw new IllegalArgumentException(e);
}
}

parse
	: properties
	;

properties
	: ( all_properties | property_list )
	;

property_list
	: property (',' property)* -> ^( 'properties' property (property)* )
	;

property
	: PROPERTY_NAME | nested_property
	;

nested_property
	: PROPERTY_NAME '{' properties '}' -> ^( 'nested_property' PROPERTY_NAME properties )
	;

all_properties
	: WILDCARD -> ^( 'properties' WILDCARD )
	;

PROPERTY_NAME
	: ( 'A'..'Z' )( 'a'..'z' | 'A'..'Z' )*
	;

WILDCARD
	: '*'
	;

WS
	: ( ' ' | '\t' | '\r' | '\n' ) {$channel=HIDDEN;}
	;