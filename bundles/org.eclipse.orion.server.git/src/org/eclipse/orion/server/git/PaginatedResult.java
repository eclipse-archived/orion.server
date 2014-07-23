package org.eclipse.orion.server.git;

import org.json.JSONArray;

public class PaginatedResult {

	public PaginatedResult(JSONArray children, boolean hasNext) {
		this.children = children;
		this.hasNext = hasNext;
	}

	public PaginatedResult() {

	};

	private JSONArray children = new JSONArray();;
	private boolean hasNext = false;

	public JSONArray getChildren() {
		return children;
	}

	public void setChildren(JSONArray children) {
		this.children = children;
	}

	public void setHasNext(boolean hasNext) {
		this.hasNext = hasNext;
	}

	public boolean hasNext() {
		return hasNext;
	}

	public boolean isEmpty() {
		return children.length() == 0;
	}
}
