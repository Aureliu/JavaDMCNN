// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2001
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

/**
 * an abstract class for both inactive edges (complete parse tree nodes) and
 * active edges (partially matched productions).
 */

public abstract class Edge {
	/**
	 * the category of this node: either a String (grammar symbol) or a Literal.
	 */

	public Object category;

	/**
	 * for non-leaf nodes, the children of this node. (For leaf nodes, = null.)
	 */

	public ParseTreeNode[] children;

	/**
	 * the first character (of the document) spanned by this node
	 */

	public int start;

	/**
	 * the last character (of the document) spanned by this node
	 */

	public int end;

	/**
	 * Returns children of this edge.
	 *
	 * @return
	 */
	public ParseTreeNode[] getChildren() {
		return children;
	}
}
