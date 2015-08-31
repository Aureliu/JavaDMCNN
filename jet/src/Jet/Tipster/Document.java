// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Tipster;

import java.io.Serializable;
import java.util.*;

import Jet.Lisp.*;
import Jet.Refres.Resolve; // for normalizeName
import Jet.Parser.SyntacticRelationSet;

/**
 * Document provides a container for the text of a document and the annotations
 * on a document.
 * <p>
 * <i>Hypotheses:</i> Each annotation on a document can be associated with a
 * hypothesis. If <code>currentHypothesis</code> (set by
 * {@link #setCurrentHypothesis}) is non-null, each annotation added to the
 * document is given a feature <b>hypo</b> with that value. If
 * <code>activeHypotheses</code> (set by {@link #setActiveHypotheses}) is
 * non-null, methods which return annotations only return those annotations
 * whose <b>hypo</b> feature is among the <code>activeHypotheses</code>.
 */

public class Document implements Serializable {
	StringBuffer text;

	// annotationsByStart is a mapping from starting positions to vectors of
	// annotations
	private Hashtable annotationsByStart;

	// annotationsByEnd is a mapping from starting positions to vectors of
	// annotations
	private Hashtable annotationsByEnd;

	// annotationsByType is a mapping from types to vectors of annotations
	private Hashtable annotationsByType;

	private int nextAnnotationId;

	private Object currentHypothesis = null;

	private Set activeHypotheses = null;

	/**
	 * Creates a new document with no text or annotations.
	 */

	public Document() {
		text = new StringBuffer();
		annotationsByStart = new Hashtable();
		annotationsByEnd = new Hashtable();
		annotationsByType = new Hashtable();
		nextAnnotationId = 0;
		relations = new SyntacticRelationSet();
	}

	/**
	 * Creates a new document with text <I>stg</I> and no annotations.
	 */

	public Document(String stg) {
		text = new StringBuffer(stg);
		annotationsByStart = new Hashtable();
		annotationsByEnd = new Hashtable();
		annotationsByType = new Hashtable();
		nextAnnotationId = 0;
		relations = new SyntacticRelationSet();
	}

	public Document(Document doc) {
		text = new StringBuffer(doc.text);
		annotationsByStart = new Hashtable();
		annotationsByEnd = new Hashtable();
		annotationsByType = new Hashtable();
		nextAnnotationId = 0;
		String[] types = doc.getAnnotationTypes();
		for (int i = 0; i < types.length; i++) {
			Vector v = doc.annotationsOfType(types[i]);
			for (int j = 0; j < v.size(); j++) {
				Annotation a = (Annotation) v.get(j);
				FeatureSet fs = null;
				if (a.attributes != null) {
					fs = new FeatureSet(a.attributes);
				}
				annotate(types[i], a.span(), fs);
			}
		}
		relations = new SyntacticRelationSet();
	}

	/**
	 * Deletes the text and all annotations on a document, creating an empty
	 * document.
	 */

	public void clear() {
		text.setLength(0);
		annotationsByStart.clear();
		annotationsByEnd.clear();
		annotationsByType.clear();
		nextAnnotationId = 0;
		relations = new SyntacticRelationSet();
	}

	/**
	 * Sets the text of a document. <B>Warning</B>: this should not be done if
	 * the document has annotations.
	 */

	public void setText(String stg) {
		text = new StringBuffer(stg);
	}

	/**
	 * Returns the entire text of the document.
	 */

	public String text() {
		return text.toString();
	}

	/**
	 * Returns the text subsumed by span <I>s</I>.
	 */

	public String text(Span s) {
		return text.substring(s.start(), s.end());
	}

	/**
	 * Returns the text subsumed by annotation <I>ann</I>.
	 */

	public String text(Annotation ann) {
		return text(ann.span());
	}

	/**
	 * Returns the text subsumed by span <I>s</I>, with leading and trailing
	 * whitespace removed, and other whitespace sequences replaced by a single
	 * blank.
	 */

	public String normalizedText(Span s) {
		return Resolve.normalizeName(text.substring(s.start(), s.end()));
	}

	/**
	 * Returns the text subsumed by annotation <I>ann</I>, with leading and
	 * trailing whitespace removed, and other whitespace sequences replaced by a
	 * single blank.
	 */

	public String normalizedText(Annotation ann) {
		return normalizedText(ann.span());
	}

	/**
	 * Adds the text <I>stg</I> to the end of the document.
	 */

	public StringBuffer append(String stg) {
		return text.append(stg);
	}

	/**
	 * Adds the char <I>c</I> to the end of the document.
	 */

	public StringBuffer append(char c) {
		return text.append(c);
	}

	/**
	 * Returns the length of the document (in characters).
	 */

	public int length() {
		return text.length();
	}

	/**
	 * Returns a Span covering the entire document.
	 */

	public Span fullSpan() {
		return new Span(0, text.length());
	}

	/**
	 * Returns the character at position <I>posn</I> in the document.
	 */

	public char charAt(int posn) {
		return text.charAt(posn);
	}

	/**
	 * Sets the character at position <I>posn</I> to <I>c</I>.
	 */

	public void setCharAt(int posn, char c) {
		text.setCharAt(posn, c);
	}

	/**
	 * Removes all annotations on the document.
	 */

	public void clearAnnotations() {
		annotationsByStart.clear();
		annotationsByEnd.clear();
		annotationsByType.clear();
	}

	/**
	 * Adds an annotation to the document.
	 */

	public Annotation addAnnotation(Annotation ann) {
		// index by starting and ending position
		Integer start = new Integer(ann.span.start);
		if (!annotationsByStart.containsKey(start))
			annotationsByStart.put(start, new Vector());
		Vector vs = (Vector) annotationsByStart.get(start);
		vs.add(ann);
		Integer end = new Integer(ann.span.end);
		if (!annotationsByEnd.containsKey(end))
			annotationsByEnd.put(end, new Vector());
		Vector ve = (Vector) annotationsByEnd.get(end);
		ve.add(ann);
		// index by type
		String type = ann.type;
		if (!annotationsByType.containsKey(type))
			annotationsByType.put(type, new Vector());
		Vector vt = (Vector) annotationsByType.get(type);
		vt.add(ann);

		// if there is an activeHypothesis, add it to annotation
		if (currentHypothesis != null)
			ann.put("hypo", currentHypothesis);

		// set Document pointer within annotation
		Span s = ann.span();
		s.setDocument(this);

		return ann;
	}

	/**
	 * Creates an annotation and adds it to the document.
	 */

	public Annotation annotate(String tp, Span sp, FeatureSet att) {
		// System.out.println ("Adding annotation " + tp + sp);
		return this.addAnnotation(new Annotation(tp, sp, att));
	}

	/**
	 * Removes annotation <I>ann</I> from the document. Does nothing if ann is
	 * not an annotation on the document.
	 */

	public void removeAnnotation(Annotation ann) {
		Integer start = new Integer(ann.span.start);
		Vector vs = (Vector) annotationsByStart.get(start);
		if (vs != null)
			vs.remove(ann);

		Integer end = new Integer(ann.span.end);
		Vector ve = (Vector) annotationsByEnd.get(end);
		if (ve != null)
			ve.remove(ann);

		String type = ann.type;
		Vector vt = (Vector) annotationsByType.get(type);
		if (vt != null)
			vt.remove(ann);
	}

	/**
	 * removes all annotations of type 'type' from the document.
	 */

	public void removeAnnotationsOfType(String type) {
		Vector v = annotationsOfType(type);
		if (v == null)
			return;
		for (int i = 0; i < v.size(); i++) {
			Annotation a = (Annotation) v.get(i);
			removeAnnotation(a);
		}
	}

	/**
	 * Returns the annotations beginning at character position <I>start</I>.
	 * Returns <B>null</B> if there are no annotations starting at this
	 * position.
	 */

	public Vector<Annotation> annotationsAt(int start) {
		Integer s = new Integer(start);
		Vector v = (Vector) annotationsByStart.get(s);
		if (v == null)
			return null;
		return activeAnnotations(v);
	}

	/**
	 * Returns the annotations of type <I>type</I> beginning at character
	 * position <I>start</I>. If there are no annotations of this type, returns
	 * <CODE>null</CODE>.
	 */

	public Vector<Annotation> annotationsAt(int start, String type) {
		Vector annAt = this.annotationsAt(start);
		if (annAt == null)
			return null;
		Vector result = null;
		for (int i = 0; i < annAt.size(); i++) {
			Annotation ann = (Annotation) annAt.get(i);
			if (ann.type().equals(type)) {
				if (result == null)
					result = new Vector();
				result.addElement(ann);
			}
		}
		return result;
	}

	/**
	 * Returns the annotations which begin at character position <I>start</I>
	 * and whose type is in array <I>types</I>. If there are no such annotations, 
	 * returns <CODE>null</CODE>.
	 */

	public Vector<Annotation> annotationsAt(int start, String[] types) {
		Vector<Annotation> annAt = this.annotationsAt(start);
		if (annAt == null)
			return null;
		Vector result = null;
		for (Annotation ann : annAt) {
			for (String type : types) {
				if (ann.type().equals(type)) {
					if (result == null)
						result = new Vector();
					result.addElement(ann);
				}
			}
		}
		return result;
	}

	/**
	 * Returns the annotations ending at character position <I>ending</I>.
	 * Returns <B>null</B> if there are no annotations ending at this position.
	 */

	public Vector<Annotation> annotationsEndingAt(int end) {
		Integer e = new Integer(end);
		Vector v = (Vector) annotationsByEnd.get(e);
		if (v == null)
			return null;
		return activeAnnotations(v);
	}

	/**
	 * Returns the annotations of type <I>type</I> ending at character position
	 * <I>end</I>. If there are no annotations of this type, returns <CODE>null</CODE>.
	 */

	public Vector<Annotation> annotationsEndingAt(int end, String type) {
		Vector annAt = this.annotationsEndingAt(end);
		if (annAt == null)
			return null;
		Vector result = null;
		for (int i = 0; i < annAt.size(); i++) {
			Annotation ann = (Annotation) annAt.get(i);
			if (ann.type().equals(type)) {
				if (result == null)
					result = new Vector();
				result.addElement(ann);
			}
		}
		return result;
	}

	/**
	 * Returns the token annotation starting at position <I>start</I>, or
	 * <B>null</B> if no token starts at this position.
	 */

	public Annotation tokenAt(int start) {
		Vector annAt = this.annotationsAt(start);
		if (annAt == null)
			return null;
		for (int i = 0; i < annAt.size(); i++) {
			Annotation ann = (Annotation) annAt.get(i);
			if (ann.type().equals("token"))
				return ann;
		}
		return null;
	}

	/**
	 * Returns the token annotation ending at position <I>end</I>, or <B>null</B>
	 * if no token starts at this position.
	 */

	public Annotation tokenEndingAt(int end) {
		Vector annAt = this.annotationsEndingAt(end);
		if (annAt == null)
			return null;
		for (int i = 0; i < annAt.size(); i++) {
			Annotation ann = (Annotation) annAt.get(i);
			if (ann.type().equals("token"))
				return ann;
		}
		return null;
	}

	/**
	 * Returns a vector of all annotations of type <I>type</I>. Returns <B>null</B>
	 * if there are no annotations of this type.
	 * return all parameter(type) in annotations by type 
	 */

	public Vector<Annotation> annotationsOfType(String type) {
		Vector v = (Vector) annotationsByType.get(type);
		return activeAnnotations(v);
	}

	/**
	 * Returns a vector of all annotations of type <I>type</I> whose span is
	 * contained within <I>span</I>. If <I>span</I> is <CODE>null</CODE>,
	 * all annotations of that type are returned. Returns <CODE>null</CODE> if
	 * there are no annotations starting at this position.
	 */

	public Vector<Annotation> annotationsOfType(String type, Span span) {
		Vector v = annotationsOfType(type);
		if (v == null)
			return null;
		if (span == null)
			return v;
		Vector result = new Vector();
		for (int i = 0; i < v.size(); i++) {
			Annotation a = (Annotation) v.get(i);
			if (a.span().within(span))
				result.add(a);
		}
		if (result.size() > 0)
			return result;
		else
			return null;
	}

	/**
	 * sets the value of <code>currentHypothesis</code>. If
	 * <code>currentHypothesis</code> is non-null, a <b>hypo</b> feature with
	 * this value is added to every new annotation on this document.
	 */

	public void setCurrentHypothesis(Object hypoId) {
		currentHypothesis = hypoId;
	}

	/**
	 * sets the value of <code>activeHypotheses</code>. If
	 * <code>activeHypotheses</code> is non-null, methods which retrieve
	 * annotations on a document only return those annotations whose <b>hypo</b>
	 * value is in <code>activeHypotheses</code>.
	 */

	public void setActiveHypotheses(Set hypoIdSet) {
		activeHypotheses = hypoIdSet;
	}

	/**
	 * if <code>activeHypotheses</code> is non-null, return those annotations
	 * in <code>anns</code> whose <b>hypo</b> value is in
	 * <code>activeHypotheses</code>.
	 */

	private Vector<Annotation> activeAnnotations(Vector anns) {
		if (anns == null)
			return null;
		if (activeHypotheses == null)
			return (Vector) anns.clone();
		Vector newanns = new Vector(anns.size());
		for (int i = 0; i < anns.size(); i++) {
			Annotation a = (Annotation) anns.get(i);
			Object hypoId = a.get("hypo");
			if (hypoId == null || activeHypotheses.contains(hypoId))
				newanns.add(a);
		}
		return newanns;
	}

	/**
	 * Returns a vector of all annotation types. Returns <B>null</B> if there
	 * are no annotation types. <B>Warning:</B> do not modify the returned
	 * vector. Doing so can affect the annotations stored on a document.
	 */

	public String[] getAnnotationTypes() {
		return (String[]) annotationsByType.keySet().toArray(new String[0]);
	}

	/**
	 * annotateWithTag annotates document with <CODE>Span</CODE> of text
	 * between <I>&lt;tag&gt;</I> and <I>&lt;/tag&gt;</I>. Sets type of
	 * annotation to <I>tag</I> name.
	 *
	 * @param tag
	 *            name of a tag to find a <CODE>Span</CODE> between tags
	 * @param start
	 *            where to start searching for a <I>tag</I>
	 * @param end
	 *            where to end searching for a <I>tag</I>
	 */

	public void annotateWithTag(String tag, int start, int end) {
		String closeTag = "/" + tag;
		int tagStart = 0, tagEnd = 0;
		FeatureSet fs = new FeatureSet();
		StringBuffer token = null;
		boolean collecting = false;

		for (int i = start; i < end && i < text.length(); ++i) {
			char c = text.charAt(i);	//@ Di whole text 
			if (c == '<') {
				collecting = true;
				token = new StringBuffer();
				tagEnd = i - 1;
			} else if (collecting) {
				if (c == '>') {
					collecting = false;
					// check if we found open tag
					String tagName = token.toString();
					if (tagName.equalsIgnoreCase(tag)) {
						tagStart = i + 1;
						// check if we found close tag, we are done
					} else if (tagName.equalsIgnoreCase(closeTag)) {
						if (tagStart != 0 && tagEnd != 0 && tagStart < tagEnd) {
							annotate(tag, new Span(tagStart, tagEnd), fs);
						}
						tagStart = 0;
					}
				} else {
					token.append(c);
				}
			}
		}
	}

	/**
	 * annotateWithTag annotates document with <CODE>Span</CODE> of text
	 * between <I>&lt;tag&gt;</I> and <I>&lt;/tag&gt;</I>. Sets type of
	 * annotation to <I>tag</I> name.
	 *
	 * @param tag
	 *            name of a tag to find a <CODE>Span</CODE> between tags
	 */

	public void annotateWithTag(String tag) {
		annotateWithTag(tag, 0, length());
	}

	/**
	 * returns a unique integer for this Document, to be used in assigning an
	 * 'id' feature to an Annotation on this Document.
	 */

	public int getNextAnnotationId() {
		return nextAnnotationId++;
	}

	/**
	 * right margin for wrapping (inserting newlines) into sgml tags for
	 * writeSGML. = 0 if no wrapping.
	 */

	private int sgmlWrapMargin = 80;

	/**
	 * set right margin for wrapping (inserting newlines) into sgml tags. for
	 * writeSGML. = 0 if no wrapping. Initial value = 80.
	 */

	public void setSGMLwrapMargin(int n) {
		sgmlWrapMargin = n;
	}

	/**
	 * amount to indent sgml tags, per level of tag nesting, for writeSGML. = 0
	 * if no indentation.
	 */

	private int sgmlIndent = 0;

	/**
	 * set amount to indent sgml tags, per level of tag nesting, for writeSGML. =
	 * 0 if no indentation.
	 */

	public void setSGMLindent(int n) {
		sgmlIndent = n;
	}

	/**
	 * Returns the text of the document with each instance of an annotation of
	 * type <I>type</I> enclosed in SGML tags. If <I>type</I> == <B>null</B>,
	 * all annotations are included.
	 * <P>
	 * A Jet Document may contain annotations that are not nested, but these
	 * cannot be represented in SGML or XML. If the endpoint of an annotation is
	 * greated than the endpoint of a preceding annotation that is still open,
	 * the annotation is not written out.
	 */

	public StringBuffer writeSGML(String type) {
		if (type == null)
			return writeSGML(null, 0, text.length());
		else
			return writeSGML(new String[]{type}, 0, text.length());
	}

	/**
	 * performs writeSGML over portion 'span' of the Document.
	 */

	public StringBuffer writeSGML(String type, Span span) {
		if (type == null)
			return writeSGML(null, span.start(), span.end());
		else
			return writeSGML(new String[]{type}, span.start(), span.end());
	}

	boolean strictNesting = false;

	/**
	 * performs writeSGML for characters 'start' through 'end' of the Document,
	 * generating tags for annotations whose type appears in array 'types'.
	 */

	public StringBuffer writeSGML(String[] types, int start, int end) {
		prepareToWriteSGML(types);
		StringBuffer result = new StringBuffer();
		int column = 0;
		Stack<Annotation> openAnnotations = new Stack<Annotation>();
		Vector<Annotation> annotationsStartingHere;
		for (int ichar = start; ichar < end; ichar++) {
			// get annotations starting at this position (ichar)
			if (types == null)
				annotationsStartingHere = this.annotationsAt(ichar);
			else
				annotationsStartingHere = this.annotationsAt(ichar, types);
			if (annotationsStartingHere != null) {
				// sort annotations by ending position (highest end value first)
				Annotation.sort(annotationsStartingHere);

				// check for proper nesting of annotations
				if (strictNesting) {
					if (!openAnnotations.empty()) {
						int furthestCloseOfNewAnnotations =
							annotationsStartingHere.get(0).span().end();
						int nextClose =
							openAnnotations.peek().span().end();
						if (furthestCloseOfNewAnnotations > nextClose) {
							System.err.println ("writeSGML:  annotations not nested.");
							System.err.println (annotationsStartingHere.get(0) + " crosses " +
							                    openAnnotations.peek());
						}
					}
				} else {
					for (Annotation ann : annotationsStartingHere) {
						for (int i = openAnnotations.size() - 1; i >= 0; i--) {
							Annotation openAnn = openAnnotations.get(i);
							if (ann.type().equals(openAnn.type)) {
								if (ann.span().end() > openAnn.span().end()) {
									System.err.println ("writeSGML:  annotations of same type not nested.");
									System.err.println (openAnn + " crosses " + ann);
								}
								break;
							}
						}
					}
				}				

				// generate opening tags
				for (Annotation ann : annotationsStartingHere) {
					int annEnd = ann.span().end();
					if (annEnd > end) {
						System.err.println ("writeSGML:  annotation extending past end of span was suppressed");
						System.err.println ("            " + ann);
						continue;
					}
					String annString = ann.toSGMLString();
					if (sgmlIndent > 0) {
						if (openAnnotations.empty()) {
							column += annString.length();
						} else {
							annString = "<\n" + blankString(openAnnotations.size() * sgmlIndent)
									+ annString.substring(1);
							column = annString.length() - 2;
						}
					} else if (sgmlWrapMargin > 0 && column + annString.length() > sgmlWrapMargin) {
						annString = annString.substring(0, annString.length() - 1) + "\n>";
						column = 1;
					} else {
						column += annString.length();
					}
					result.append(annString);
					openAnnotations.push(ann);
				}
				Annotation.sort(openAnnotations);
			}
			// generate all immediately closing tags
			while ((!openAnnotations.empty())
					&& (((Annotation) openAnnotations.peek()).span().end() == ichar)) {
				String annType = ((Annotation) openAnnotations.peek()).type();
				String annString = "</" + annType + ">";
				result.append(annString);
				openAnnotations.pop();
			}
			// write character
			result.append(text.charAt(ichar));
			if (text.charAt(ichar) == '\n') {
				column = 0;
			} else {
				column++;
			}
			// generate all closing tags following character
			while ((!openAnnotations.empty())
					&& (((Annotation) openAnnotations.peek()).span().end() == (1 + ichar))) {
				String annType = ((Annotation) openAnnotations.peek()).type();
				if (sgmlIndent > 0) {
					result.append("<\n" + blankString((openAnnotations.size() - 1) * sgmlIndent)
							+ "/" + annType + ">");
					column = annType.length() + 1;
				} else {
					result.append("</" + annType + ">");
					column += annType.length() + 3;
				}
				openAnnotations.pop();
			}
		}
		// check that all closing tags have been generated
		while (!openAnnotations.empty()) {
			System.err.println("Internal writeSGML error:  didn't close annotation "
					+ openAnnotations.pop());
		}
		return result;
	}

	/**
	 * prepares for writeSGML by assigning 'id' features to Annotations on the
	 * Document as needed. If an Annotation is referenced by a feature of an
	 * Annotation which is to be written out, the first Annotation must be
	 * assigned an 'id'.
	 */

	private void prepareToWriteSGML(String[] types) {
		Vector annotationsStartingHere;
		for (int ichar = 0; ichar < text.length(); ichar++) {
			if (types == null)
				annotationsStartingHere = this.annotationsAt(ichar);
		else
			annotationsStartingHere = this.annotationsAt(ichar, types);
			if (annotationsStartingHere != null) {
				for (int i = 0; i < annotationsStartingHere.size(); i++) {
					Annotation ann = (Annotation) annotationsStartingHere.get(i);
					ann.prepareToMakeString(1);
				}
			}
		}
	}

	/**
	 * returns a string of 'n' blanks.
	 */

	private String blankString(int len) {
		StringBuffer sb = new StringBuffer(len);
		for (int i = 0; i < len; i++)
			sb.append(' ');
		return sb.toString();
	}

	/**
	 * extend the endpoint of Annotation ann to include the following whitespace
	 * past the current endpoint. However, do not extent the endpoint past the
	 * current starting point of any other annotation in order to avoid
	 * situations where annotations overlap but are not nested.
	 */

	public void stretch(Annotation ann) {
		removeAnnotation(ann);
		Span s = ann.span();
		int posn = s.end();
		while (posn < text.length() && annotationsByStart.get(posn) == null
				&& Character.isWhitespace(charAt(posn)))
			posn++;
		s.setEnd(posn);
		addAnnotation(ann);
	}

	/**
	 * extend the endpoint, using {@link #stretch(Annotation) stretch}, of all
	 * annotations of type <CODE>type</CODE>.
	 */

	public void stretch(String type) {
		Vector v = annotationsOfType(type);
		if (v == null)
			return;
		for (int i = 0; i < v.size(); i++) {
			Annotation ann = (Annotation) v.get(i);
			stretch(ann);
		}
	}

	/**
	 * extend the endpoint, using {@link #stretch(Annotation) stretch}, of all
	 * annotations in the document.
	 */

	public void stretchAll() {
		String[] types = getAnnotationTypes();
		for (int i = 0; i < types.length; i++)
			stretch(types[i]);
	}

	/**
	 * shrink the endpoint of Annotation <CODE>code</CODE> to remove any
	 * trailing whitespace.
	 */

	public void shrink(Annotation ann) {
		removeAnnotation(ann);
		Span s = ann.span();
		int start = s.start();
		int posn = s.end();
		while (posn > start && Character.isWhitespace(charAt(posn - 1)))
			posn--;
		s.setEnd(posn);
		addAnnotation(ann);
	}

	/**
	 * shrink the endpoint, using {@link #shrink(Annotation) shrink}, of all
	 * annotations of type <CODE>type</CODE>, to eliminate trailing
	 * whitespace.
	 */

	public void shrink(String type) {
		Vector v = annotationsOfType(type);
		if (v == null)
			return;
		for (int i = 0; i < v.size(); i++) {
			Annotation ann = (Annotation) v.get(i);
			shrink(ann);
		}
	}

	/**
	 * shrink the endpoint, using {@link #shrink(Annotation) shrink}, of all
	 * annotations in the document.
	 */

	public void shrinkAll() {
		String[] types = getAnnotationTypes();
		for (int i = 0; i < types.length; i++)
			shrink(types[i]);
	}

	public SyntacticRelationSet relations;
}
