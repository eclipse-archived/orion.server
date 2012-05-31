// $ANTLR 3.2 Sep 23, 2009 12:02:23 src/org/eclipse/orion/server/core/resources/ResourceShape.g 2012-05-14 11:04:18

package org.eclipse.orion.server.core.resources;

import org.antlr.runtime.*;

public class ResourceShapeLexer extends Lexer {
	public static final int T__9 = 9;
	public static final int T__10 = 10;
	public static final int PROPERTIES = 4;
	public static final int T__11 = 11;
	public static final int PROPERTY_NAME = 6;
	public static final int WILDCARD = 7;
	public static final int NESTED_PROPERTIES = 5;
	public static final int WS = 8;
	public static final int EOF = -1;

	@Override
	public void reportError(RecognitionException e) {
		throw new IllegalArgumentException(e);
	}

	// delegates
	// delegators

	public ResourceShapeLexer() {
		;
	}

	public ResourceShapeLexer(CharStream input) {
		this(input, new RecognizerSharedState());
	}

	public ResourceShapeLexer(CharStream input, RecognizerSharedState state) {
		super(input, state);

	}

	public String getGrammarFileName() {
		return "src/org/eclipse/orion/server/core/resources/ResourceShape.g";
	}

	// $ANTLR start "PROPERTIES"
	public final void mPROPERTIES() throws RecognitionException {
		try {
			int _type = PROPERTIES;
			int _channel = DEFAULT_TOKEN_CHANNEL;
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:13:12: ( 'properties' )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:13:14: 'properties'
			{
				match("properties");

			}

			state.type = _type;
			state.channel = _channel;
		} finally {
		}
	}

	// $ANTLR end "PROPERTIES"

	// $ANTLR start "NESTED_PROPERTIES"
	public final void mNESTED_PROPERTIES() throws RecognitionException {
		try {
			int _type = NESTED_PROPERTIES;
			int _channel = DEFAULT_TOKEN_CHANNEL;
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:14:19: ( 'nested_property' )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:14:21: 'nested_property'
			{
				match("nested_property");

			}

			state.type = _type;
			state.channel = _channel;
		} finally {
		}
	}

	// $ANTLR end "NESTED_PROPERTIES"

	// $ANTLR start "T__9"
	public final void mT__9() throws RecognitionException {
		try {
			int _type = T__9;
			int _channel = DEFAULT_TOKEN_CHANNEL;
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:15:6: ( ',' )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:15:8: ','
			{
				match(',');

			}

			state.type = _type;
			state.channel = _channel;
		} finally {
		}
	}

	// $ANTLR end "T__9"

	// $ANTLR start "T__10"
	public final void mT__10() throws RecognitionException {
		try {
			int _type = T__10;
			int _channel = DEFAULT_TOKEN_CHANNEL;
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:16:7: ( '{' )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:16:9: '{'
			{
				match('{');

			}

			state.type = _type;
			state.channel = _channel;
		} finally {
		}
	}

	// $ANTLR end "T__10"

	// $ANTLR start "T__11"
	public final void mT__11() throws RecognitionException {
		try {
			int _type = T__11;
			int _channel = DEFAULT_TOKEN_CHANNEL;
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:17:7: ( '}' )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:17:9: '}'
			{
				match('}');

			}

			state.type = _type;
			state.channel = _channel;
		} finally {
		}
	}

	// $ANTLR end "T__11"

	// $ANTLR start "PROPERTY_NAME"
	public final void mPROPERTY_NAME() throws RecognitionException {
		try {
			int _type = PROPERTY_NAME;
			int _channel = DEFAULT_TOKEN_CHANNEL;
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:60:2: ( ( 'A' .. 'Z' ) ( 'a' .. 'z' | 'A' .. 'Z' )* )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:60:4: ( 'A' .. 'Z' ) ( 'a' .. 'z' | 'A' .. 'Z' )*
			{
				// src/org/eclipse/orion/server/core/resources/ResourceShape.g:60:4: ( 'A' .. 'Z' )
				// src/org/eclipse/orion/server/core/resources/ResourceShape.g:60:6: 'A' .. 'Z'
				{
					matchRange('A', 'Z');

				}

				// src/org/eclipse/orion/server/core/resources/ResourceShape.g:60:16: ( 'a' .. 'z' | 'A' .. 'Z' )*
				loop1: do {
					int alt1 = 2;
					int LA1_0 = input.LA(1);

					if (((LA1_0 >= 'A' && LA1_0 <= 'Z') || (LA1_0 >= 'a' && LA1_0 <= 'z'))) {
						alt1 = 1;
					}

					switch (alt1) {
						case 1 :
						// src/org/eclipse/orion/server/core/resources/ResourceShape.g:
						{
							if ((input.LA(1) >= 'A' && input.LA(1) <= 'Z') || (input.LA(1) >= 'a' && input.LA(1) <= 'z')) {
								input.consume();

							} else {
								MismatchedSetException mse = new MismatchedSetException(null, input);
								recover(mse);
								throw mse;
							}

						}
							break;

						default :
							break loop1;
					}
				} while (true);

			}

			state.type = _type;
			state.channel = _channel;
		} finally {
		}
	}

	// $ANTLR end "PROPERTY_NAME"

	// $ANTLR start "WILDCARD"
	public final void mWILDCARD() throws RecognitionException {
		try {
			int _type = WILDCARD;
			int _channel = DEFAULT_TOKEN_CHANNEL;
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:64:2: ( '*' )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:64:4: '*'
			{
				match('*');

			}

			state.type = _type;
			state.channel = _channel;
		} finally {
		}
	}

	// $ANTLR end "WILDCARD"

	// $ANTLR start "WS"
	public final void mWS() throws RecognitionException {
		try {
			int _type = WS;
			int _channel = DEFAULT_TOKEN_CHANNEL;
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:68:2: ( ( ' ' | '\\t' | '\\r' | '\\n' ) )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:68:4: ( ' ' | '\\t' | '\\r' | '\\n' )
			{
				if ((input.LA(1) >= '\t' && input.LA(1) <= '\n') || input.LA(1) == '\r' || input.LA(1) == ' ') {
					input.consume();

				} else {
					MismatchedSetException mse = new MismatchedSetException(null, input);
					recover(mse);
					throw mse;
				}

				_channel = HIDDEN;

			}

			state.type = _type;
			state.channel = _channel;
		} finally {
		}
	}

	// $ANTLR end "WS"

	public void mTokens() throws RecognitionException {
		// src/org/eclipse/orion/server/core/resources/ResourceShape.g:1:8: ( PROPERTIES | NESTED_PROPERTIES | T__9 | T__10 | T__11 | PROPERTY_NAME | WILDCARD | WS )
		int alt2 = 8;
		switch (input.LA(1)) {
			case 'p' : {
				alt2 = 1;
			}
				break;
			case 'n' : {
				alt2 = 2;
			}
				break;
			case ',' : {
				alt2 = 3;
			}
				break;
			case '{' : {
				alt2 = 4;
			}
				break;
			case '}' : {
				alt2 = 5;
			}
				break;
			case 'A' :
			case 'B' :
			case 'C' :
			case 'D' :
			case 'E' :
			case 'F' :
			case 'G' :
			case 'H' :
			case 'I' :
			case 'J' :
			case 'K' :
			case 'L' :
			case 'M' :
			case 'N' :
			case 'O' :
			case 'P' :
			case 'Q' :
			case 'R' :
			case 'S' :
			case 'T' :
			case 'U' :
			case 'V' :
			case 'W' :
			case 'X' :
			case 'Y' :
			case 'Z' : {
				alt2 = 6;
			}
				break;
			case '*' : {
				alt2 = 7;
			}
				break;
			case '\t' :
			case '\n' :
			case '\r' :
			case ' ' : {
				alt2 = 8;
			}
				break;
			default :
				NoViableAltException nvae = new NoViableAltException("", 2, 0, input);

				throw nvae;
		}

		switch (alt2) {
			case 1 :
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:1:10: PROPERTIES
			{
				mPROPERTIES();

			}
				break;
			case 2 :
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:1:21: NESTED_PROPERTIES
			{
				mNESTED_PROPERTIES();

			}
				break;
			case 3 :
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:1:39: T__9
			{
				mT__9();

			}
				break;
			case 4 :
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:1:44: T__10
			{
				mT__10();

			}
				break;
			case 5 :
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:1:50: T__11
			{
				mT__11();

			}
				break;
			case 6 :
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:1:56: PROPERTY_NAME
			{
				mPROPERTY_NAME();

			}
				break;
			case 7 :
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:1:70: WILDCARD
			{
				mWILDCARD();

			}
				break;
			case 8 :
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:1:79: WS
			{
				mWS();

			}
				break;

		}

	}

}