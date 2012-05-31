// $ANTLR 3.2 Sep 23, 2009 12:02:23 src/org/eclipse/orion/server/core/resources/ResourceShape.g 2012-05-14 11:04:17

package org.eclipse.orion.server.core.resources;

import org.antlr.runtime.*;
import org.antlr.runtime.tree.*;

public class ResourceShapeParser extends Parser {
	public static final String[] tokenNames = new String[] {"<invalid>", "<EOR>", "<DOWN>", "<UP>", "PROPERTIES", "NESTED_PROPERTIES", "PROPERTY_NAME", "WILDCARD", "WS", "','", "'{'", "'}'"};
	public static final int T__9 = 9;
	public static final int T__10 = 10;
	public static final int PROPERTIES = 4;
	public static final int T__11 = 11;
	public static final int PROPERTY_NAME = 6;
	public static final int WILDCARD = 7;
	public static final int NESTED_PROPERTIES = 5;
	public static final int WS = 8;
	public static final int EOF = -1;

	// delegates
	// delegators

	public ResourceShapeParser(TokenStream input) {
		this(input, new RecognizerSharedState());
	}

	public ResourceShapeParser(TokenStream input, RecognizerSharedState state) {
		super(input, state);

	}

	protected TreeAdaptor adaptor = new CommonTreeAdaptor();

	public void setTreeAdaptor(TreeAdaptor adaptor) {
		this.adaptor = adaptor;
	}

	public TreeAdaptor getTreeAdaptor() {
		return adaptor;
	}

	public String[] getTokenNames() {
		return ResourceShapeParser.tokenNames;
	}

	public String getGrammarFileName() {
		return "src/org/eclipse/orion/server/core/resources/ResourceShape.g";
	}

	@Override
	public void reportError(RecognitionException e) {
		throw new IllegalArgumentException(e);
	}

	public static class parse_return extends ParserRuleReturnScope {
		CommonTree tree;

		public Object getTree() {
			return tree;
		}
	};

	// $ANTLR start "parse"
	// src/org/eclipse/orion/server/core/resources/ResourceShape.g:35:1: parse : properties ;
	public final ResourceShapeParser.parse_return parse() throws RecognitionException {
		ResourceShapeParser.parse_return retval = new ResourceShapeParser.parse_return();
		retval.start = input.LT(1);

		CommonTree root_0 = null;

		ResourceShapeParser.properties_return properties1 = null;

		try {
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:36:2: ( properties )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:36:4: properties
			{
				root_0 = (CommonTree) adaptor.nil();

				pushFollow(FOLLOW_properties_in_parse86);
				properties1 = properties();

				state._fsp--;

				adaptor.addChild(root_0, properties1.getTree());

			}

			retval.stop = input.LT(-1);

			retval.tree = (CommonTree) adaptor.rulePostProcessing(root_0);
			adaptor.setTokenBoundaries(retval.tree, retval.start, retval.stop);

		} catch (RecognitionException re) {
			reportError(re);
			recover(input, re);
			retval.tree = (CommonTree) adaptor.errorNode(input, retval.start, input.LT(-1), re);

		} finally {
		}
		return retval;
	}

	// $ANTLR end "parse"

	public static class properties_return extends ParserRuleReturnScope {
		CommonTree tree;

		public Object getTree() {
			return tree;
		}
	};

	// $ANTLR start "properties"
	// src/org/eclipse/orion/server/core/resources/ResourceShape.g:39:1: properties : ( all_properties | property_list ) ;
	public final ResourceShapeParser.properties_return properties() throws RecognitionException {
		ResourceShapeParser.properties_return retval = new ResourceShapeParser.properties_return();
		retval.start = input.LT(1);

		CommonTree root_0 = null;

		ResourceShapeParser.all_properties_return all_properties2 = null;

		ResourceShapeParser.property_list_return property_list3 = null;

		try {
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:40:2: ( ( all_properties | property_list ) )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:40:4: ( all_properties | property_list )
			{
				root_0 = (CommonTree) adaptor.nil();

				// src/org/eclipse/orion/server/core/resources/ResourceShape.g:40:4: ( all_properties | property_list )
				int alt1 = 2;
				int LA1_0 = input.LA(1);

				if ((LA1_0 == WILDCARD)) {
					alt1 = 1;
				} else if ((LA1_0 == PROPERTY_NAME)) {
					alt1 = 2;
				} else {
					NoViableAltException nvae = new NoViableAltException("", 1, 0, input);

					throw nvae;
				}
				switch (alt1) {
					case 1 :
					// src/org/eclipse/orion/server/core/resources/ResourceShape.g:40:6: all_properties
					{
						pushFollow(FOLLOW_all_properties_in_properties100);
						all_properties2 = all_properties();

						state._fsp--;

						adaptor.addChild(root_0, all_properties2.getTree());

					}
						break;
					case 2 :
					// src/org/eclipse/orion/server/core/resources/ResourceShape.g:40:23: property_list
					{
						pushFollow(FOLLOW_property_list_in_properties104);
						property_list3 = property_list();

						state._fsp--;

						adaptor.addChild(root_0, property_list3.getTree());

					}
						break;

				}

			}

			retval.stop = input.LT(-1);

			retval.tree = (CommonTree) adaptor.rulePostProcessing(root_0);
			adaptor.setTokenBoundaries(retval.tree, retval.start, retval.stop);

		} catch (RecognitionException re) {
			reportError(re);
			recover(input, re);
			retval.tree = (CommonTree) adaptor.errorNode(input, retval.start, input.LT(-1), re);

		} finally {
		}
		return retval;
	}

	// $ANTLR end "properties"

	public static class property_list_return extends ParserRuleReturnScope {
		CommonTree tree;

		public Object getTree() {
			return tree;
		}
	};

	// $ANTLR start "property_list"
	// src/org/eclipse/orion/server/core/resources/ResourceShape.g:43:1: property_list : property ( ',' property )* -> ^( 'properties' property ( property )* ) ;
	public final ResourceShapeParser.property_list_return property_list() throws RecognitionException {
		ResourceShapeParser.property_list_return retval = new ResourceShapeParser.property_list_return();
		retval.start = input.LT(1);

		CommonTree root_0 = null;

		Token char_literal5 = null;
		ResourceShapeParser.property_return property4 = null;

		ResourceShapeParser.property_return property6 = null;

		CommonTree char_literal5_tree = null;
		RewriteRuleTokenStream stream_9 = new RewriteRuleTokenStream(adaptor, "token 9");
		RewriteRuleSubtreeStream stream_property = new RewriteRuleSubtreeStream(adaptor, "rule property");
		try {
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:44:2: ( property ( ',' property )* -> ^( 'properties' property ( property )* ) )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:44:4: property ( ',' property )*
			{
				pushFollow(FOLLOW_property_in_property_list117);
				property4 = property();

				state._fsp--;

				stream_property.add(property4.getTree());
				// src/org/eclipse/orion/server/core/resources/ResourceShape.g:44:13: ( ',' property )*
				loop2: do {
					int alt2 = 2;
					int LA2_0 = input.LA(1);

					if ((LA2_0 == 9)) {
						alt2 = 1;
					}

					switch (alt2) {
						case 1 :
						// src/org/eclipse/orion/server/core/resources/ResourceShape.g:44:14: ',' property
						{
							char_literal5 = (Token) match(input, 9, FOLLOW_9_in_property_list120);
							stream_9.add(char_literal5);

							pushFollow(FOLLOW_property_in_property_list122);
							property6 = property();

							state._fsp--;

							stream_property.add(property6.getTree());

						}
							break;

						default :
							break loop2;
					}
				} while (true);

				// AST REWRITE
				// elements: property, property, PROPERTIES
				// token labels: 
				// rule labels: retval
				// token list labels: 
				// rule list labels: 
				// wildcard labels: 
				retval.tree = root_0;
				RewriteRuleSubtreeStream stream_retval = new RewriteRuleSubtreeStream(adaptor, "rule retval", retval != null ? retval.tree : null);

				root_0 = (CommonTree) adaptor.nil();
				// 44:29: -> ^( 'properties' property ( property )* )
				{
					// src/org/eclipse/orion/server/core/resources/ResourceShape.g:44:32: ^( 'properties' property ( property )* )
					{
						CommonTree root_1 = (CommonTree) adaptor.nil();
						root_1 = (CommonTree) adaptor.becomeRoot((CommonTree) adaptor.create(PROPERTIES, "PROPERTIES"), root_1);

						adaptor.addChild(root_1, stream_property.nextTree());
						// src/org/eclipse/orion/server/core/resources/ResourceShape.g:44:57: ( property )*
						while (stream_property.hasNext()) {
							adaptor.addChild(root_1, stream_property.nextTree());

						}
						stream_property.reset();

						adaptor.addChild(root_0, root_1);
					}

				}

				retval.tree = root_0;
			}

			retval.stop = input.LT(-1);

			retval.tree = (CommonTree) adaptor.rulePostProcessing(root_0);
			adaptor.setTokenBoundaries(retval.tree, retval.start, retval.stop);

		} catch (RecognitionException re) {
			reportError(re);
			recover(input, re);
			retval.tree = (CommonTree) adaptor.errorNode(input, retval.start, input.LT(-1), re);

		} finally {
		}
		return retval;
	}

	// $ANTLR end "property_list"

	public static class property_return extends ParserRuleReturnScope {
		CommonTree tree;

		public Object getTree() {
			return tree;
		}
	};

	// $ANTLR start "property"
	// src/org/eclipse/orion/server/core/resources/ResourceShape.g:47:1: property : ( PROPERTY_NAME | nested_property );
	public final ResourceShapeParser.property_return property() throws RecognitionException {
		ResourceShapeParser.property_return retval = new ResourceShapeParser.property_return();
		retval.start = input.LT(1);

		CommonTree root_0 = null;

		Token PROPERTY_NAME7 = null;
		ResourceShapeParser.nested_property_return nested_property8 = null;

		CommonTree PROPERTY_NAME7_tree = null;

		try {
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:48:2: ( PROPERTY_NAME | nested_property )
			int alt3 = 2;
			int LA3_0 = input.LA(1);

			if ((LA3_0 == PROPERTY_NAME)) {
				int LA3_1 = input.LA(2);

				if ((LA3_1 == 10)) {
					alt3 = 2;
				} else if ((LA3_1 == EOF || LA3_1 == 9 || LA3_1 == 11)) {
					alt3 = 1;
				} else {
					NoViableAltException nvae = new NoViableAltException("", 3, 1, input);

					throw nvae;
				}
			} else {
				NoViableAltException nvae = new NoViableAltException("", 3, 0, input);

				throw nvae;
			}
			switch (alt3) {
				case 1 :
				// src/org/eclipse/orion/server/core/resources/ResourceShape.g:48:4: PROPERTY_NAME
				{
					root_0 = (CommonTree) adaptor.nil();

					PROPERTY_NAME7 = (Token) match(input, PROPERTY_NAME, FOLLOW_PROPERTY_NAME_in_property151);
					PROPERTY_NAME7_tree = (CommonTree) adaptor.create(PROPERTY_NAME7);
					adaptor.addChild(root_0, PROPERTY_NAME7_tree);

				}
					break;
				case 2 :
				// src/org/eclipse/orion/server/core/resources/ResourceShape.g:48:20: nested_property
				{
					root_0 = (CommonTree) adaptor.nil();

					pushFollow(FOLLOW_nested_property_in_property155);
					nested_property8 = nested_property();

					state._fsp--;

					adaptor.addChild(root_0, nested_property8.getTree());

				}
					break;

			}
			retval.stop = input.LT(-1);

			retval.tree = (CommonTree) adaptor.rulePostProcessing(root_0);
			adaptor.setTokenBoundaries(retval.tree, retval.start, retval.stop);

		} catch (RecognitionException re) {
			reportError(re);
			recover(input, re);
			retval.tree = (CommonTree) adaptor.errorNode(input, retval.start, input.LT(-1), re);

		} finally {
		}
		return retval;
	}

	// $ANTLR end "property"

	public static class nested_property_return extends ParserRuleReturnScope {
		CommonTree tree;

		public Object getTree() {
			return tree;
		}
	};

	// $ANTLR start "nested_property"
	// src/org/eclipse/orion/server/core/resources/ResourceShape.g:51:1: nested_property : PROPERTY_NAME '{' properties '}' -> ^( 'nested_property' PROPERTY_NAME properties ) ;
	public final ResourceShapeParser.nested_property_return nested_property() throws RecognitionException {
		ResourceShapeParser.nested_property_return retval = new ResourceShapeParser.nested_property_return();
		retval.start = input.LT(1);

		CommonTree root_0 = null;

		Token PROPERTY_NAME9 = null;
		Token char_literal10 = null;
		Token char_literal12 = null;
		ResourceShapeParser.properties_return properties11 = null;

		CommonTree PROPERTY_NAME9_tree = null;
		CommonTree char_literal10_tree = null;
		CommonTree char_literal12_tree = null;
		RewriteRuleTokenStream stream_11 = new RewriteRuleTokenStream(adaptor, "token 11");
		RewriteRuleTokenStream stream_PROPERTY_NAME = new RewriteRuleTokenStream(adaptor, "token PROPERTY_NAME");
		RewriteRuleTokenStream stream_10 = new RewriteRuleTokenStream(adaptor, "token 10");
		RewriteRuleSubtreeStream stream_properties = new RewriteRuleSubtreeStream(adaptor, "rule properties");
		try {
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:52:2: ( PROPERTY_NAME '{' properties '}' -> ^( 'nested_property' PROPERTY_NAME properties ) )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:52:4: PROPERTY_NAME '{' properties '}'
			{
				PROPERTY_NAME9 = (Token) match(input, PROPERTY_NAME, FOLLOW_PROPERTY_NAME_in_nested_property167);
				stream_PROPERTY_NAME.add(PROPERTY_NAME9);

				char_literal10 = (Token) match(input, 10, FOLLOW_10_in_nested_property169);
				stream_10.add(char_literal10);

				pushFollow(FOLLOW_properties_in_nested_property171);
				properties11 = properties();

				state._fsp--;

				stream_properties.add(properties11.getTree());
				char_literal12 = (Token) match(input, 11, FOLLOW_11_in_nested_property173);
				stream_11.add(char_literal12);

				// AST REWRITE
				// elements: PROPERTY_NAME, NESTED_PROPERTIES, properties
				// token labels: 
				// rule labels: retval
				// token list labels: 
				// rule list labels: 
				// wildcard labels: 
				retval.tree = root_0;
				RewriteRuleSubtreeStream stream_retval = new RewriteRuleSubtreeStream(adaptor, "rule retval", retval != null ? retval.tree : null);

				root_0 = (CommonTree) adaptor.nil();
				// 52:37: -> ^( 'nested_property' PROPERTY_NAME properties )
				{
					// src/org/eclipse/orion/server/core/resources/ResourceShape.g:52:40: ^( 'nested_property' PROPERTY_NAME properties )
					{
						CommonTree root_1 = (CommonTree) adaptor.nil();
						root_1 = (CommonTree) adaptor.becomeRoot((CommonTree) adaptor.create(NESTED_PROPERTIES, "NESTED_PROPERTIES"), root_1);

						adaptor.addChild(root_1, stream_PROPERTY_NAME.nextNode());
						adaptor.addChild(root_1, stream_properties.nextTree());

						adaptor.addChild(root_0, root_1);
					}

				}

				retval.tree = root_0;
			}

			retval.stop = input.LT(-1);

			retval.tree = (CommonTree) adaptor.rulePostProcessing(root_0);
			adaptor.setTokenBoundaries(retval.tree, retval.start, retval.stop);

		} catch (RecognitionException re) {
			reportError(re);
			recover(input, re);
			retval.tree = (CommonTree) adaptor.errorNode(input, retval.start, input.LT(-1), re);

		} finally {
		}
		return retval;
	}

	// $ANTLR end "nested_property"

	public static class all_properties_return extends ParserRuleReturnScope {
		CommonTree tree;

		public Object getTree() {
			return tree;
		}
	};

	// $ANTLR start "all_properties"
	// src/org/eclipse/orion/server/core/resources/ResourceShape.g:55:1: all_properties : WILDCARD -> ^( 'properties' WILDCARD ) ;
	public final ResourceShapeParser.all_properties_return all_properties() throws RecognitionException {
		ResourceShapeParser.all_properties_return retval = new ResourceShapeParser.all_properties_return();
		retval.start = input.LT(1);

		CommonTree root_0 = null;

		Token WILDCARD13 = null;

		CommonTree WILDCARD13_tree = null;
		RewriteRuleTokenStream stream_WILDCARD = new RewriteRuleTokenStream(adaptor, "token WILDCARD");

		try {
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:56:2: ( WILDCARD -> ^( 'properties' WILDCARD ) )
			// src/org/eclipse/orion/server/core/resources/ResourceShape.g:56:4: WILDCARD
			{
				WILDCARD13 = (Token) match(input, WILDCARD, FOLLOW_WILDCARD_in_all_properties196);
				stream_WILDCARD.add(WILDCARD13);

				// AST REWRITE
				// elements: WILDCARD, PROPERTIES
				// token labels: 
				// rule labels: retval
				// token list labels: 
				// rule list labels: 
				// wildcard labels: 
				retval.tree = root_0;
				RewriteRuleSubtreeStream stream_retval = new RewriteRuleSubtreeStream(adaptor, "rule retval", retval != null ? retval.tree : null);

				root_0 = (CommonTree) adaptor.nil();
				// 56:13: -> ^( 'properties' WILDCARD )
				{
					// src/org/eclipse/orion/server/core/resources/ResourceShape.g:56:16: ^( 'properties' WILDCARD )
					{
						CommonTree root_1 = (CommonTree) adaptor.nil();
						root_1 = (CommonTree) adaptor.becomeRoot((CommonTree) adaptor.create(PROPERTIES, "PROPERTIES"), root_1);

						adaptor.addChild(root_1, stream_WILDCARD.nextNode());

						adaptor.addChild(root_0, root_1);
					}

				}

				retval.tree = root_0;
			}

			retval.stop = input.LT(-1);

			retval.tree = (CommonTree) adaptor.rulePostProcessing(root_0);
			adaptor.setTokenBoundaries(retval.tree, retval.start, retval.stop);

		} catch (RecognitionException re) {
			reportError(re);
			recover(input, re);
			retval.tree = (CommonTree) adaptor.errorNode(input, retval.start, input.LT(-1), re);

		} finally {
		}
		return retval;
	}

	// $ANTLR end "all_properties"

	// Delegated rules

	public static final BitSet FOLLOW_properties_in_parse86 = new BitSet(new long[] {0x0000000000000002L});
	public static final BitSet FOLLOW_all_properties_in_properties100 = new BitSet(new long[] {0x0000000000000002L});
	public static final BitSet FOLLOW_property_list_in_properties104 = new BitSet(new long[] {0x0000000000000002L});
	public static final BitSet FOLLOW_property_in_property_list117 = new BitSet(new long[] {0x0000000000000202L});
	public static final BitSet FOLLOW_9_in_property_list120 = new BitSet(new long[] {0x0000000000000040L});
	public static final BitSet FOLLOW_property_in_property_list122 = new BitSet(new long[] {0x0000000000000202L});
	public static final BitSet FOLLOW_PROPERTY_NAME_in_property151 = new BitSet(new long[] {0x0000000000000002L});
	public static final BitSet FOLLOW_nested_property_in_property155 = new BitSet(new long[] {0x0000000000000002L});
	public static final BitSet FOLLOW_PROPERTY_NAME_in_nested_property167 = new BitSet(new long[] {0x0000000000000400L});
	public static final BitSet FOLLOW_10_in_nested_property169 = new BitSet(new long[] {0x00000000000000C0L});
	public static final BitSet FOLLOW_properties_in_nested_property171 = new BitSet(new long[] {0x0000000000000800L});
	public static final BitSet FOLLOW_11_in_nested_property173 = new BitSet(new long[] {0x0000000000000002L});
	public static final BitSet FOLLOW_WILDCARD_in_all_properties196 = new BitSet(new long[] {0x0000000000000002L});

}