// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Tipster;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import Jet.Lisp.FeatureSet;

/**
 * An Annotation assigns a type and a set of features to a portion of a
 * Document.
 */

public class Annotation {
	String type;

	Span span;

	FeatureSet attributes;

	public Annotation(String tp, Span sp, FeatureSet att) {
		type = tp;
		span = sp;
		attributes = att;
	}

	public String type() {
		return type;
	}

	/**
	 * returns the span (of text) associated with the annotation.
	 */

	public Span span() {
		return span;
	}

	/**
	 * returns the start of the span associated with the annotation.
	 */

	public int start() {
		return span.start();
	}

	/**
	 * returns the end of the span associated with the annotation.
	 */

	public int end() {
		return span.end();
	}

	public FeatureSet attributes() {
		return attributes;
	}

	/**
	 * Returns the value of attribute <I>feature</I> of the annotation.
	 */

	public Object get(String feature) {
		if (attributes == null)
			return null;
		else
			return attributes.get(feature);
	}

	/**
	 * Sets the value of attribute <I>feature</I> of the annotation to <I>value</I>.
	 */

	public void put(String feature, Object value) {
		if (attributes == null)
			attributes = new FeatureSet();
		attributes.put(feature, value);
	}
	
	public void remove(String feature) {
		if (attributes != null) {
			attributes.remove(feature);
		}
	}

	/**
	 * if the Annotation does not already have an 'id' feature, assigns an 'id'
	 * feature whose value is the unique (to the Document) identifier obtained
	 * by {@link Document#getNextAnnotationId()}.
	 */

	public void assignId() {
		if (get("id") != null)
			return;
		Document doc = span.document();
		if (doc == null) {
			System.out.println("Annotation.assignId failed:  null document field in " + toString());
			return;
		}
		int id = doc.getNextAnnotationId();
		put("id", new Integer(id));
	}

	/**
	 * returns the value of the 'id' feature (converted from an Integer or
	 * String to an int). Prints a message if the Annotation does not have an
	 * 'id' feature, or if the feature is not of the proper type.
	 */

	public int getId() {
		Object id = get("id");
		if (id == null) {
			System.out.println("Annotation.getId failed:  no id for Annotation.");
			return 0;
		} else if (id instanceof Integer) {
			return ((Integer) id).intValue();
		} else if (id instanceof String) {
			try {
				return Integer.parseInt((String) id);
			} catch (NumberFormatException e) {
				System.out.println("getId:  invalid id attribute " + id);
				return 0;
			}
		} else {
			System.out.println("getId:  invalid id attribute " + id);
			return 0;
		}
	}

	/**
	 * add 'id' feature to all Annotations referenced by this Annotation. This
	 * is invoked prior to converting this Annotation to a String, if annotation
	 * references are to be converted to '#nnn' form.
	 */

	public void prepareToMakeString(int nestingLimit) {
		if (attributes != null)
			attributes.prepareToMakeString(nestingLimit);
	}

	/**
	 * in converting an Annotation to a String, the maximum level of nesting of
	 * Annotations which will be printed. Nesting occurs when the value of some
	 * attribute of an Annotation is itself an Annotation. When the maximum
	 * level is reached, the reference to the Annotation is represented by
	 * '#id'.
	 */

	public static final int MAX_ANNOTATION_DEPTH = 2;

	/**
	 * returns a String representation of the Annotation, including the type,
	 * span, and attributes, enclosed in '<' and '>'.
	 */

	public String toString() {
		if (attributes == null)
			return "<" + type + " " + span + ">";
		else
			return "<" + type + " " + span + " "
					+ attributes.toSGMLString(MAX_ANNOTATION_DEPTH, false, false) + ">";
	}

	public String toSGMLString() {
		return toSGMLString(1, true, true);
	}

	/**
	 * returns a String representation of the Annotation, without information
	 * about the span of the annotation. The representation serves as the open
	 * tag in a SGML or XML represenation of the annotated document.
	 *
	 * @param nestingLimit
	 *            limit of depth of nested Annotations (Annotations which are
	 *            referenced as values of features of this Annotation) which
	 *            will be represented in the String.
	 * @param useIds
	 *            if true, references to Annotations which are nested more
	 *            deeply than 'nestingLimit' are rendered as '#nnn', where 'nnn'
	 *            is the 'id' feature of the referenced Annotation; if false,
	 *            these references are rendered as "..."
	 * @param quoteValues
	 *            if true, values of features are enclosed in double quotes, as
	 *            required by XML.
	 */

	public String toSGMLString(int nestingLimit, boolean useIds, boolean quoteValues) {
		if (attributes == null || attributes.size() == 0)
			return "<" + type + ">";
		else
			return "<" + type + " " + attributes.toSGMLString(nestingLimit, useIds, quoteValues)
					+ ">";
	}

	/**
	 * sorts a Vector of Annotations based on the end of the span of each
	 * Annotation, highest end value first. Among annotations with the same end
	 * span, if the annotations are linked in a tree structure by 'children'
	 * attributes, the highest annotation in the tree is placed first in the
	 * sorted sequence.
	 */

	public static void sort(Vector annotations) {
		if (annotations == null)
			return;
		for (int i = 0; i < annotations.size() - 1; i++) {
			for (int j = i + 1; j < annotations.size(); j++) {
				Annotation anni = (Annotation) annotations.get(i);
				Annotation annj = (Annotation) annotations.get(j);
				if (anni.span.end() < annj.span.end()) {
					annotations.set(i, annj);
					annotations.set(j, anni);
				}
				if (anni.span.end() == annj.span.end() && annj.get("children") != null) {
					Annotation[] children = (Annotation[]) annj.get("children");
					for (int c = 0; c < children.length; c++) {
						if (children[c] == anni) {
							annotations.set(i, annj);
							annotations.set(j, anni);
							break;
						}
					}
				}
			}
		}
	}

	/**
	 * sorts a list of annotation order by its start position.
	 * @param annotations
	 */
	public static void sortByStartPosition(List<Annotation> annotations) {
		if (annotations == null) {
			return;
		}
		Collections.sort(annotations, StartPositionComparator.instance);
	}

	/**
	 * Comparator for annotation based on its start position.
	 *
	 * @author Akira ODA
	 */
	private static class StartPositionComparator implements Comparator<Annotation> {
		public static StartPositionComparator instance = new StartPositionComparator();

		private StartPositionComparator() {
		}

		public int compare(Annotation a, Annotation b) {
			if (a.start() < b.start()) {
				return -1;
			} else if (a.start() > b.start()) {
				return 1;
			} else {
				return 0;
			}
		}
	}
}
