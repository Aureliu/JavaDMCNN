// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

import Jet.Tipster.*;
import Jet.Lisp.*;

/**
 * a node of a parse tree.
 */

public class ParseTreeNode extends Edge {

	/**
	 * for leaf nodes, the (token or constit) annotation matched by this node.
	 * For non-leaf nodes, = null in parser output.
	 * StatParser.makeParseAnnotations sets this field to the annotation
	 * generated from this node.
	 */

	public Annotation ann;

	/**
	 * for leaf nodes, the word matched by this node. (For non-leaf nodes, =
	 * null.)
	 */

	public String word;

	/**
	 * for non-leaf nodes, the number of the child (starting at 1) which is the
	 * head of this node. = 0 if not set.
	 */

	public int head;

	/**
	 * function tag. if function tag is not set, this is be null.
	 */
	public String function;

	/**
	 * create a ParseTreeNode corresponding to a leaf of the parse tree.
	 */
	public ParseTreeNode(Object category, ParseTreeNode[] children, int start,
			int end, Annotation ann, String word) {
		this(category, children, start, end, ann, word, null);
	}

	/**
	 * create a ParseTreeNode corresponding to a leaf of the parse tree.
	 */
	public ParseTreeNode(Object category, ParseTreeNode[] children, int start,
			int end, Annotation ann, String word, String function) {
		this.category = category;
		this.children = children;
		this.start = start;
		this.end = end;
		this.ann = ann;
		this.word = word;
		this.head = 0;
		this.function = function;
	}

	/**
	 * create a ParseTreeNode corresponding to an internal node of the parse
	 * tree.
	 */
	public ParseTreeNode(Object category, ParseTreeNode[] children, int start,
			int end, int head) {
		this(category, children, start, end, head, null);
	}

	/**
	 * create a ParseTreeNode corresponding to an internal node of the parse
	 * tree.
	 */
	public ParseTreeNode(Object category, ParseTreeNode[] children, int start, int end, int head, String function) {
		this.category = category;
		this.children = children;
		this.start = start;
		this.end = end;
		this.ann = null;
		this.word = null;
		this.head = head;
		this.function = function;
	}

	/**
	 * return a String representation of the ParseTreeNode. This consists of the
	 * grammar category, followed -- only for terminal, non-literal nodes -- by
	 * the sentence word matched.
	 */

	public String toString() {
		if (category instanceof Literal)
			return category.toString();
		else if (word != null)
			return ((String) category) + " = " + word;
		else
			return (String) category;
	}

	/**
	 * prints the parse tree rooted at this node in an indented form.
	 */

	public void printTree() {
		this.printTree(0);
	}

	private void printTree(int indent) {
		for (int i = 0; i < indent; i++)
			Jet.Console.print(" ");
		Jet.Console.println(this.toString());
		//String s = category.toString();
		if (children != null && children.length > 0) {
			for (int i = 0; i < children.length; i++)
				children[i].printTree(indent + 4);
		}
	}

	/**
	 * given a parse tree in the form of nested ParseTreeNodes, adds an
	 * Annotation of type 'constit' to Document 'doc' for each non-terminal node
	 * in the tree. (Annotations are already present for terminal nodes, as a
	 * prerequisite for parsing.) Returns the Annotation associated with the
	 * root node of the tree.
	 */

	public static Annotation makeParseAnnotations(Document doc, ParseTreeNode n) {
		if (n.children == null) {
			return n.ann;
		} else {
			int childCount = n.children.length;
			Annotation[] children = new Annotation[childCount];
			for (int i = 0; i < childCount; i++) {
				children[i] = makeParseAnnotations(doc, n.children[i]);
			}
			Annotation head = null;
			if (n.head > 0 && n.head <= children.length)
				head = children[n.head - 1];
			Annotation a = new Annotation("constit", new Span(n.start, n.end),
					new FeatureSet("cat", n.category, "children", children,
							"headC", head));
			doc.addAnnotation(a);
			n.ann = a;
			return a;
		}
	}

	/**
	 * given a parse tree Annotation 'node', as created by makeParseAnnotations,
	 * returns an array containing the children of 'node', or null if the node
	 * has no children.
	 *
	 * @param node
	 *            an Annotation representing a parse tree node (an Annotation of
	 *            type 'constit').
	 */

	public static Annotation[] children(Annotation node) {
		if (node == null)
			return null;
		Object c = node.get("children");
		if (c == null)
			return null;
		if (!(c instanceof Annotation[])) {
			// System.out.println ("ParseTreeNode.children: invalid children
			// attribute: " + node);
			return null;
		}
		return (Annotation[]) c;
	}


	public static void terminalToToken(Document doc, ParseTreeNode node) {
		if (node.children == null) {
			FeatureSet fs = new FeatureSet();
			doc.annotate("token", node.ann.span(), fs);
		} else {
			for (ParseTreeNode child : node.children) {
				terminalToToken(doc, child);
			}
		}
	}
}
