package org.eclipse.orion.server.tests.core;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.eclipse.orion.internal.server.core.EndingAwareLineReader;
import org.junit.Test;

public class EndingAwareLineReaderTest {

	private String TEST_STRING_1 = "line 1 \n line2 \r\n line3 \r line 4 \r ";
	private String TEST_STRING_2 = "line1 \rline2\r\nline3 testcontent \0 testcontent \r\n";

	@Test
	public void shouldProperlyReadStrings() throws IOException {
		EndingAwareLineReader reader = new EndingAwareLineReader(new StringReader(TEST_STRING_1));
		StringBuilder builder = new StringBuilder();
		while (reader.hasNext()) {
			String line = reader.getLine();
			String lineDelimiter = reader.getLineDelimiter();
			builder.append(line);
			builder.append(lineDelimiter);
		}
		assertEquals(TEST_STRING_1, builder.toString());

		reader = new EndingAwareLineReader(new StringReader(TEST_STRING_2));
		builder = new StringBuilder();
		while (reader.hasNext()) {
			String line = reader.getLine();
			String lineDelimiter = reader.getLineDelimiter();
			builder.append(line);
			builder.append(lineDelimiter);
		}
		assertEquals(TEST_STRING_2, builder.toString());
	}
}
