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
package org.eclipse.orion.server.cf.manifest.v2.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.server.cf.manifest.v2.*;
import org.eclipse.osgi.util.NLS;
import org.json.*;

public class ManifestUtils {

	private static final Pattern NON_SLUG_PATTERN = Pattern.compile("[^\\w-]"); //$NON-NLS-1$
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\s]"); //$NON-NLS-1$

	/* global defaults */
	public static final String DEFAULT_MEMORY = "512M"; //$NON-NLS-1$
	public static final String DEFAULT_INSTANCES = "1"; //$NON-NLS-1$
	public static final String DEFAULT_PATH = "."; //$NON-NLS-1$

	public static final String[] RESERVED_PROPERTIES = {//
	"env", // //$NON-NLS-1$
			"inherit", // //$NON-NLS-1$
			"applications" // //$NON-NLS-1$
	};

	public static boolean isReserved(ManifestParseTree node) {
		String value = node.getLabel();
		for (String property : RESERVED_PROPERTIES)
			if (property.equals(value))
				return true;

		return false;
	}

	public static final String[] APPLICATION_PROPERTIES = {//
	"name", "memory", "host", "buildpack", "command", //   //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$ //$NON-NLS-5$
			"domain", "instances", "path", "timeout", "no-route", "services"// //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
	};

	public static boolean isApplicationProperty(ManifestParseTree node) {
		String value = node.getLabel();
		for (String property : APPLICATION_PROPERTIES)
			if (property.equals(value))
				return true;

		return false;
	}

	/**
	 * Inner helper method parsing single manifests with additional semantic analysis.
	 */
	protected static ManifestParseTree parseManifest(InputStream inputStream, String targetBase, Analyzer analyzer) throws IOException, TokenizerException, ParserException, AnalyzerException {

		/* run preprocessor */
		ManifestPreprocessor preprocessor = new ManifestPreprocessor();
		List<InputLine> inputLines = preprocessor.process(inputStream);

		/* run parser */
		ManifestTokenizer tokenizer = new ManifestTokenizer(inputLines);
		ManifestParser parser = new ManifestParser();
		ManifestParseTree parseTree = parser.parse(tokenizer);

		/* perform inheritance transformations */
		ManifestTransformator transformator = new ManifestTransformator();
		transformator.apply(parseTree);

		/* resolve symbols */
		SymbolResolver symbolResolver = new SymbolResolver(targetBase);
		symbolResolver.apply(parseTree);

		/* validate common field values */
		Analyzer applicationAnalyzer = analyzer != null ? analyzer : new ApplicationSanizator();
		applicationAnalyzer.apply(parseTree);
		return parseTree;
	}

	/**
	 * Inner helper method parsing single manifests with additional semantic analysis.
	 */
	protected static ManifestParseTree parseManifest(IFileStore manifestFileStore, String targetBase, Analyzer analyzer) throws CoreException, IOException, TokenizerException, ParserException, AnalyzerException {

		/* basic sanity checks */
		IFileInfo manifestFileInfo = manifestFileStore.fetchInfo();
		if (!manifestFileInfo.exists() || manifestFileInfo.isDirectory())
			throw new IOException(ManifestConstants.MISSING_OR_INVALID_MANIFEST);

		if (manifestFileInfo.getLength() == EFS.NONE)
			throw new IOException(ManifestConstants.EMPTY_MANIFEST);

		if (manifestFileInfo.getLength() > ManifestConstants.MANIFEST_SIZE_LIMIT)
			throw new IOException(ManifestConstants.MANIFEST_FILE_SIZE_EXCEEDED);

		InputStream inputStream = manifestFileStore.openInputStream(EFS.NONE, null);
		return parseManifest(inputStream, targetBase, analyzer);
	}

	/**
	 * Utility method wrapping manifest parse process including inheritance and additional semantic analysis.
	 * @param sandbox The file store used to limit manifest inheritance, i.e. each parent manifest has to be a
	 *  transitive child of the sandbox.
	 * @param manifestStore Manifest file store used to fetch the manifest contents.
	 * @param targetBase Cloud foundry target base used to resolve manifest symbols.
	 * @param manifestList List of forbidden manifest paths considered in the recursive inheritance process.
	 * Used to detect inheritance cycles.
	 * @return An intermediate manifest tree representation.
	 * @throws CoreException
	 * @throws IOException
	 * @throws TokenizerException
	 * @throws ParserException
	 * @throws AnalyzerException
	 * @throws InvalidAccessException
	 */
	public static ManifestParseTree parse(IFileStore sandbox, IFileStore manifestStore, String targetBase, Analyzer analyzer, List<IPath> manifestList) throws CoreException, IOException, TokenizerException, ParserException, AnalyzerException, InvalidAccessException {
		ManifestParseTree manifest = parseManifest(manifestStore, targetBase, analyzer);

		if (!manifest.has(ManifestConstants.INHERIT))
			/* nothing to do */
			return manifest;

		/* check if the parent manifest is within the given sandbox */
		IPath parentLocation = new Path(manifest.get(ManifestConstants.INHERIT).getValue());
		if (!InheritanceUtils.isWithinSandbox(sandbox, manifestStore, parentLocation))
			throw new AnalyzerException(NLS.bind(ManifestConstants.FORBIDDEN_ACCESS_ERROR, manifest.get(ManifestConstants.INHERIT).getValue()));

		/* detect inheritance cycles */
		if (manifestList.contains(parentLocation))
			throw new AnalyzerException(ManifestConstants.INHERITANCE_CYCLE_ERROR);

		manifestList.add(parentLocation);

		IFileStore parentStore = manifestStore.getParent().getFileStore(parentLocation);
		ManifestParseTree parentManifest = parse(sandbox, parentStore, targetBase, analyzer, manifestList);
		InheritanceUtils.inherit(parentManifest, manifest);

		/* perform additional inheritance transformations */
		ManifestTransformator transformator = new ManifestTransformator();
		transformator.apply(manifest);
		return manifest;
	}

	/**
	 * Helper method for {@link #parse(IFileStore, IFileStore, String, List<IPath>)}
	 * @param sandbox
	 * @param manifestStore
	 * @return
	 * @throws CoreException
	 * @throws IOException
	 * @throws TokenizerException
	 * @throws ParserException
	 * @throws AnalyzerException
	 * @throws InvalidAccessException
	 */
	public static ManifestParseTree parse(IFileStore sandbox, IFileStore manifestStore) throws CoreException, IOException, TokenizerException, ParserException, AnalyzerException, InvalidAccessException {
		return parse(sandbox, manifestStore, null, null, new ArrayList<IPath>());
	}

	/**
	 * Helper method for {@link #parse(IFileStore, IFileStore, String, List<IPath>)}
	 * @param sandbox
	 * @param manifestStore
	 * @param targetBase
	 * @return
	 * @throws CoreException
	 * @throws IOException
	 * @throws TokenizerException
	 * @throws ParserException
	 * @throws AnalyzerException
	 * @throws InvalidAccessException
	 */
	public static ManifestParseTree parse(IFileStore sandbox, IFileStore manifestStore, String targetBase) throws CoreException, IOException, TokenizerException, ParserException, AnalyzerException, InvalidAccessException {
		return parse(sandbox, manifestStore, targetBase, null, new ArrayList<IPath>());
	}

	/**
	 * Helper method for {@link #parse(IFileStore, IFileStore, String, List<IPath>)}
	 * @param sandbox
	 * @param manifestStore
	 * @param targetBase
	 * @return
	 * @throws CoreException
	 * @throws IOException
	 * @throws TokenizerException
	 * @throws ParserException
	 * @throws AnalyzerException
	 * @throws InvalidAccessException
	 */
	public static ManifestParseTree parse(IFileStore sandbox, IFileStore manifestStore, String targetBase, Analyzer analyzer) throws CoreException, IOException, TokenizerException, ParserException, AnalyzerException, InvalidAccessException {
		return parse(sandbox, manifestStore, targetBase, analyzer, new ArrayList<IPath>());
	}

	/**
	 * Normalizes the string memory measurement to a MB integer value.
	 * @param memory Manifest memory measurement.
	 * @return Normalized MB integer value.
	 */
	public static int normalizeMemoryMeasure(String memory) {

		if (memory.toLowerCase().endsWith("m")) //$NON-NLS-1$
			return Integer.parseInt(memory.substring(0, memory.length() - 1));

		if (memory.toLowerCase().endsWith("mb")) //$NON-NLS-1$
			return Integer.parseInt(memory.substring(0, memory.length() - 2));

		if (memory.toLowerCase().endsWith("g")) //$NON-NLS-1$
			return (1024 * Integer.parseInt(memory.substring(0, memory.length() - 1)));

		if (memory.toLowerCase().endsWith("gb")) //$NON-NLS-1$
			return (1024 * Integer.parseInt(memory.substring(0, memory.length() - 2)));

		/* return default memory value, i.e. 1024 MB */
		return 1024;
	}

	/**
	 * Slugifies the given input to be reusable as URL pattern.
	 * @param input Input to be slugified.
	 * @return Slugified input
	 */
	public static String slugify(String input) {
		input = WHITESPACE_PATTERN.matcher(input).replaceAll("-"); //$NON-NLS-1$
		return NON_SLUG_PATTERN.matcher(input).replaceAll(""); //$NON-NLS-1$
	}

	/**
	 * Parses a manifest from the given JSON representation.
	 *  Note: no cross-manifest inheritance is allowed.
	 * @param manifestJSON
	 * @return
	 * @throws IllegalArgumentException
	 * @throws JSONException
	 * @throws IOException
	 * @throws TokenizerException
	 * @throws ParserException
	 * @throws AnalyzerException
	 */
	public static ManifestParseTree parse(JSONObject manifestJSON) throws IllegalArgumentException, JSONException, IOException, TokenizerException, ParserException, AnalyzerException {

		StringBuilder sb = new StringBuilder();
		sb.append("---").append(System.getProperty("line.separator")); //$NON-NLS-1$ //$NON-NLS-2$
		append(sb, manifestJSON, 0, false);

		String manifestYAML = sb.toString();
		InputStream inputStream = new ByteArrayInputStream(manifestYAML.getBytes("UTF-8")); //$NON-NLS-1$
		return parseManifest(inputStream, null, null);
	}

	private static void appendIndentation(StringBuilder sb, int indentation) {

		/* print indentation */
		for (int i = 0; i < indentation; ++i)
			sb.append(" "); //$NON-NLS-1$
	}

	private static void append(StringBuilder sb, JSONArray arr, int indentation) throws JSONException {

		for (int i = 0; i < arr.length(); ++i) {
			appendIndentation(sb, indentation);
			sb.append("-").append(" "); //$NON-NLS-1$ //$NON-NLS-2$

			Object val = arr.get(i);
			if (val instanceof String) {

				sb.append((String) val);
				sb.append(System.getProperty("line.separator")); //$NON-NLS-1$

			} else if (val instanceof JSONObject) {

				JSONObject objVal = (JSONObject) val;
				append(sb, objVal, indentation + 2, false);

			} else
				throw new IllegalArgumentException("Arrays may contain only JSON objects or string literals.");
		}
	}

	private static void append(StringBuilder sb, JSONObject obj, int indentation, boolean indentFirst) throws JSONException {

		String[] names = JSONObject.getNames(obj);
		if (names == null) {
			return;
		}
		for (int i = 0; i < names.length; ++i) {

			String prop = names[i];
			if (i != 0 || indentFirst)
				appendIndentation(sb, indentation);

			sb.append(prop).append(":"); //$NON-NLS-1$

			Object val = obj.get(prop);
			if (val instanceof String) {
				sb.append(" ").append((String) val); //$NON-NLS-1$
				sb.append(System.getProperty("line.separator")); //$NON-NLS-1$
			} else if (val instanceof Boolean) {
				sb.append(" ").append(val.toString()); //$NON-NLS-1$
				sb.append(System.getProperty("line.separator")); //$NON-NLS-1$
			} else if (val instanceof JSONObject) {
				JSONObject objVal = (JSONObject) val;
				sb.append(System.getProperty("line.separator")); //$NON-NLS-1$
				append(sb, objVal, indentation + 2, true);
			} else if (val instanceof JSONArray) {
				JSONArray arr = (JSONArray) val;
				sb.append(System.getProperty("line.separator")); //$NON-NLS-1$
				append(sb, arr, indentation);
			} else
				throw new IllegalArgumentException("Objects may contain only JSON objects, arrays or string literals.");
		}
	}

	/**
	 * Creates a manifest boilerplate consisting of one application with the given name.
	 * @param applicationName
	 * @return
	 * @throws IllegalArgumentException
	 * @throws JSONException
	 * @throws IOException
	 * @throws TokenizerException
	 * @throws ParserException
	 * @throws AnalyzerException
	 */
	public static ManifestParseTree createBoilerplate(String applicationName) throws IllegalArgumentException, JSONException, IOException, TokenizerException, ParserException, AnalyzerException {

		JSONObject application = new JSONObject();
		application.put(ManifestConstants.NAME, applicationName);

		JSONArray applications = new JSONArray();
		applications.put(application);

		JSONObject manifest = new JSONObject();
		manifest.put(ManifestConstants.APPLICATIONS, applications);

		return parse(manifest);
	}

	/**
	 * Helper method for deciding whether a manifest has multiple applications or not.
	 * @param manifest
	 * @return
	 */
	public static boolean hasMultipleApplications(ManifestParseTree manifest) {
		if (!manifest.has(ManifestConstants.APPLICATIONS))
			return false;

		ManifestParseTree applications = manifest.getOpt(ManifestConstants.APPLICATIONS);
		if (!applications.isList())
			return false;

		return applications.getChildren().size() > 1;
	}

	/**
	 * Instruments application properties by copying values from the instrumentation JSON.
	 * Note, that this method will perform a shallow instrumentation of single string properties.
	 * @param manifest
	 * @param instrumentation
	 * @throws JSONException
	 * @throws InvalidAccessException 
	 */
	public static void instrumentManifest(ManifestParseTree manifest, JSONObject instrumentation) throws JSONException, InvalidAccessException {

		if (instrumentation == null || !manifest.has(ManifestConstants.APPLICATIONS))
			return;

		List<ManifestParseTree> applications = manifest.get(ManifestConstants.APPLICATIONS).getChildren();

		if (instrumentation == null || instrumentation.length() == 0)
			return;

		for (String key : JSONObject.getNames(instrumentation)) {
			Object value = instrumentation.get(key);
			for (ManifestParseTree application : applications) {

				if (ManifestConstants.MEMORY.equals(key) && !updateMemory(application, (String) value))
					continue;

				if (value instanceof String) {
					application.put(key, (String) value);
				} else if (value instanceof JSONObject) {
					application.put(key, (JSONObject) value);
				}
			}
		}
	}

	private static boolean updateMemory(ManifestParseTree application, String value) {
		if (!application.has(ManifestConstants.MEMORY))
			return true;

		try {
			String appMemoryString = application.get(ManifestConstants.MEMORY).getValue();
			int appMemory = normalizeMemoryMeasure(appMemoryString);
			int instrumentationMemory = normalizeMemoryMeasure(value);

			return instrumentationMemory > appMemory;
		} catch (InvalidAccessException e) {
			return true;
		}
	}
}