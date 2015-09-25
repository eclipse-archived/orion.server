/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.git.objects;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IPath;

/**
 * // TODO Please add a comment for what the created type is for.
 *
 * @author fanjj
 *
 */
public class LinkedFile {


	private File file;
	private Entry<IPath,File> parent;
	private Map<IPath,File> children;
	public LinkedFile(File file, Entry<IPath,File> parent, Map<IPath,File> children){
		this.file=file;
		this.parent=parent;
		this.children=children;
	}
	public LinkedFile(File file, Map<IPath,File> children){
		this.file=file;
		this.parent=null;
		this.children=children;
	}
	/**
	 * @return the file
	 */
	public File getFile() {
		return file;
	}
	/**
	 * @param file the file to set
	 */
	public void setFile(File file) {
		this.file = file;
	}
	/**
	 * @return the parent
	 */
	public Entry<IPath, File> getParent() {
		return parent;
	}
	/**
	 * @param parent the parent to set
	 */
	public void setParent(Map.Entry<IPath, File> parent) {
		this.parent = parent;
	}
	/**
	 * @return the children
	 */
	public Map<IPath, File> getChildren() {
		return children;
	}
	/**
	 * @param children the children to set
	 */
	public void setChildren(Map<IPath, File> children) {
		this.children = children;
	}
	public LinkedFile(File file, Entry<IPath,File> parent){
		this.file=file;
		this.parent=parent;
		this.children=null;
	}
	public LinkedFile(File file){
		this.file=file;
		this.parent=null;
		this.children=null;
	}
	
}
