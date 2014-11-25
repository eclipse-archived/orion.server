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
package org.eclipse.orion.server.cf.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultipartMessageReader {

	final static protected Logger logger = LoggerFactory.getLogger(MultipartMessageReader.class);

	private byte[] messageBody;
	private int bodyIndex = 0;
	private byte[] currentPart = null;
	private final String CRLF = "\r\n";
	private final byte CR = 13;
	private final byte LF = 10;

	private final String boundaryPrefix = "--";
	private byte[] crlfAndPrefixBoundary;

	public MultipartMessageReader(String boundary, byte[] messageBody) {
		crlfAndPrefixBoundary = new String(CRLF + boundaryPrefix + boundary).getBytes();
		this.messageBody = messageBody;
	}

	private boolean setCurrentPart(int startPos, int size) {
		if (messageBody == null || startPos < 0 || size <= 0 || startPos + size >= messageBody.length) {
			return false;
		}
		currentPart = new byte[size];
		for (int i = 0; i < size; i++) {
			currentPart[i] = messageBody[startPos + i];
		}
		return true;
	}

	private int discardPartHeader(int pos) {
		//TODO: Add support for multipart messages that have part headers (the loggregator does not use that so it is superfluous right now)
		while (pos < messageBody.length) {
			if (messageBody[pos] == CR && messageBody[pos + 1] == LF) {
				pos += 2;
			} else {
				return pos;
			}
		}
		return -1;
	}

	private int findNextBoundaryStartPosition(int startPos) {
		int endPos = 0;
		int cmpBufLen = crlfAndPrefixBoundary.length;
		while (startPos + endPos < messageBody.length) {
			if (messageBody[startPos + endPos] == crlfAndPrefixBoundary[endPos]) {
				if (endPos + 1 == cmpBufLen) { // Found "\r\n--<boundary>"
					return startPos;
				} else { // a match, but not ready with whole comparison
					endPos++;
				}
			} else { // Restart the search
				startPos += endPos + 1;
				endPos = 0;
			}
		}
		return -1;
	}

	private int findPartEndPosition(int startPos) {
		return findNextBoundaryStartPosition(startPos);
	}

	private int findPartStartPosition(int startPos) {
		int pos = findNextBoundaryStartPosition(startPos);
		if (pos != -1) {
			if (pos + crlfAndPrefixBoundary.length + 2 < messageBody.length) {
				if (messageBody[pos] == CR && messageBody[pos + 1] == LF) {
					return discardPartHeader(pos + crlfAndPrefixBoundary.length + 2);
				}
			}
		}
		return -1;
	}

	public boolean readNextPart() {
		if (bodyIndex >= messageBody.length) {
			return false;
		}
		int partStartPos = findPartStartPosition(bodyIndex);
		if (partStartPos == -1) {
			bodyIndex = messageBody.length;
			return false;
		}
		int partEndPos = findPartEndPosition(partStartPos);
		if (partEndPos == -1) {
			bodyIndex = messageBody.length;
			return false;
		}
		if (setCurrentPart(partStartPos, partEndPos - partStartPos)) {
			bodyIndex = partEndPos;
			return true;
		} else {
			bodyIndex = messageBody.length;
			return false;
		}
	}

	public byte[] getPart() {
		return currentPart;
	}
}
