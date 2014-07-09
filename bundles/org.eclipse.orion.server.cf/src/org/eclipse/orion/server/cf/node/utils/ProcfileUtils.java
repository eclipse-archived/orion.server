package org.eclipse.orion.server.cf.node.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.server.core.IOUtilities;

public class ProcfileUtils {
	public interface IProcfileEntry {
		String getProcessType();

		String getCommand();
	}

	static class Entry implements IProcfileEntry {
		private String processType;
		private String command;

		public Entry(String processType, String command) {
			this.processType = processType;
			this.command = command;
		}

		public String getProcessType() {
			return processType;
		}

		public String getCommand() {
			return command;
		}
	};

	/**
	 * Parses a <a href="https://devcenter.heroku.com/articles/procfile#declaring-process-types">Procfile</a>.
	 * 
	 * <p>The Procfile format is one process type per line, with each line containing:
	 * <pre>&lt;process type&gt:&lt;whitespace&gt;&lt;command&gt;</pre>
	 * </p>
	 * <ul>
	 * <li><i>process type</i>: an alphanumeric string</li>
	 * <li><i>whitespace</i>: one or more whitespace characters</li>
	 * <li><i>process type</i>: the command line</li>
	 * </ul>
	 * @param store
	 * @return A list of all the processes in the Procfile
	 * @throws CoreException
	 * @throws IOException
	 */
	public static Entry[] parseProcfile(IFileStore store) throws CoreException, IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(store.openInputStream(EFS.NONE, null)));
			String line;
			List<Entry> entries = new ArrayList<Entry>();
			while ((line = reader.readLine()) != null) {
				int colonPos = line.indexOf(":");
				if (colonPos == -1)
					continue;

				// skip whitespace
				int i;
				for (i = colonPos + 1; Character.isWhitespace(line.charAt(i)); i++);

				// rest is the command
				entries.add(new ProcfileUtils.Entry(line.substring(0, colonPos), line.substring(i)));
			}
			return entries.toArray(new Entry[entries.size()]);
		} finally {
			IOUtilities.safeClose(reader);
		}
	}
}
