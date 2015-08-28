// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.03
//Description:  A Java-based Information Extraction Tool

package Jet.Scorer;

import java.util.*;
import java.io.*;

import Jet.Lisp.FeatureSet;
import Jet.Tipster.*;
import Jet.Pat.PatternSyntaxError;
import Jet.Console;

/**
 * methods for converting SGML markup into Annotations.  Blanks are not
 * allowed within tags, except for a single whitespace character before
 * a feature name.  Feature values may be enclosed in single quotes (')
 * or double quotes(");  if values do not contain whitespace or a close
 * tag bracket (>), they need not be enclosed in quotes. Only limited
 * error checking is done.
 *
 * XML declarations are read but ignored.
 */

public class SGMLProcessor {

	/**
	 *   if true, whitespace following end tag is included as part of
	 *   span assigned to annotation.
	 */

	public static boolean includeWhitespace = true;

    // the following represent the possible states of the SGML reader

	private static final int COLLECTING_TEXT       = 0;
	private static final int COLLECTING_TYPE       = 1;
	private static final int COLLECTING_FEATURE    = 2;
	private static final int COLLECTING_VALUE      = 3;
	private static final int COLLECTING_ESCAPED_VALUE = 4;
	private static final int COLLECTING_TAG_END    = 5;
	private static final int SKIPPING_XML_DECLARATION = 6;

	/**
	 *  if true, all tags will be converted to Annotations.
	 */

	public static boolean allTags = false;

	/**
	 *  a list of tags which do not have corresponding close tags and so are
	 *  to be converted to empty Annotations.
	 */

	public static String[] emptyTags = null;

	static Stack<String> openTagType;
	static Stack<Integer> openTagPosn;
	static Stack<FeatureSet> openTagFeatureSet;
	static ArrayList<Annotation> newAnnotations;

	/**
	 * Converts an SGML-marked String <I>sgmlText</I> to a <CODE>Document</CODE>
	 * instance with <I>tag</I> tags removed from the text and <I>tag</I>
	 * annotations added to the document.<p>
	 * Tags should have the exact form of &lt;type [feature=value]*&gt; or
	 * &lt;/type&gt;.<p>
	 * @param tag type of tag
	 * @return the document that has <I>tag</I> annotations added but
	 * <I>tag</I> tags removed.
	 */

	public static Document sgmlToDoc(String sgmlText, String tag) {
		Document doc = new Document();
		return sgmlToDoc (doc, sgmlText, tag);
	}

	/**
	 * Takes a <CODE>Document</CODE> <I>doc</I> whose text contains
	 * SGML markup;  deletes all existing annotations and returns the
	 * <I>doc</I> with <I>tag</I> tags removed from the text and <I>tag</I>
	 * annotations added to the document.<p>
	 * Tags should have the exact form of &lt;type [feature=value]*&gt; or
	 * &lt;/type&gt;.<p>
	 * @param tag type of tag
	 * @return the document that has <I>tag</I> annotations added but
	 * <I>tag</I> tags removed.
	 */

	public static Document sgmlToDoc(Document doc, String tag) {
		doc.clearAnnotations();
		return sgmlToDoc(doc, doc.text(), tag);
	}

	public static Document sgmlToDoc(Document doc, String sgmlText, String tag) {
		String[] tags = new String[1];
		tags[0] = tag;
		return sgmlToDoc (doc, sgmlText, tags);
	}

	/**
	 * Converts an SGML-marked String <I>sgmlText</I> to a <CODE>Document</CODE>
	 * instance with <I>tags</I> tags removed from the text and <I>tags</I>
	 * annotations added to the document.<p>
	 * Tags should have the exact form of &lt;type [feature=value]*&gt; or
	 * &lt;/type&gt;.<p>
	 * @param tags array of types of tag
	 * @return the document that has <I>tags</I> annotations added but
	 * <I>tags</I> tags removed.
	 */

	public static Document sgmlToDoc(String sgmlText, String[] tags) {
		Document doc = new Document();
		return sgmlToDoc (doc, sgmlText, tags);
	}

	public static Document sgmlToDoc(Document doc, String[] tags) {
		doc.clearAnnotations();
		return sgmlToDoc(doc, doc.text(), tags);
	}

	/**
	 *  if strictNesting is true, an error is reported if tags cross
	 *  (are not nested with respect to other tags).
	 */  
	static boolean strictNesting = false;

	public static Document sgmlToDoc(Document doc, String sgmlText, String[] tags) {

		newAnnotations = new ArrayList<Annotation>();
		StringBuffer type = null;
		StringBuffer whitespaceBeforeType = null;
		StringBuffer feature = null;
		StringBuffer value = null;
		FeatureSet fs = null;
		String tagName = "";

		int state = COLLECTING_TEXT;
		char valueDelimiter = ' ';
		int annotationStart = 0;
		String annotationType = null;
		FeatureSet annotationFS = null;
		// -- stacks holding info on open tags --
		// (for which we have found a start tag but not yet a close tag)
		openTagType = new Stack<String>();
		openTagPosn = new Stack<Integer>();
		openTagFeatureSet = new Stack<FeatureSet>();

		for (int i = 0; i < sgmlText.length(); i++) {
			char c = sgmlText.charAt(i);
			if (state == COLLECTING_TEXT) {
				if (c == '<') {
					state = COLLECTING_TYPE;
					type = new StringBuffer();
					whitespaceBeforeType = new StringBuffer();
					fs = new FeatureSet();
				} else {
					doc.append(c);
				}
			} else if (state == COLLECTING_TYPE) {
				if (Character.isWhitespace(c)) {
					if (type.length() > 0 && !type.equals("/")) {
						tagName = type.toString().intern();
						if (tagName == "?xml") {
							state = SKIPPING_XML_DECLARATION;
						} else if (tagName.charAt(0) != '/' && tagToCapture(tagName, tags)) {
							state = COLLECTING_FEATURE;
							feature = new StringBuffer();
							value = new StringBuffer();
						} else {
							doc.append('<' + whitespaceBeforeType.toString() + type.toString() + ' ');
							state = COLLECTING_TEXT;
						}
					} else {
						whitespaceBeforeType.append(c);
					}
				} else if (c == '/' && type.length() > 0) {
					tagName = type.toString().intern();
					state = COLLECTING_TAG_END;
				} else if (c == '>') {
					tagName = type.toString();
					if (tagName.length() > 0 && tagName.charAt(0) != '/' && 
					    tagToCapture(tagName, tags)) {
						endOfOpenTag (doc, tagName, fs);
						state = COLLECTING_TEXT;
					} else if (tagName.length() > 0 && tagName.charAt(0) == '/' &&
					           tagToCapture(tagName.substring(1), tags)) {
						int istack = openTagType.search(tagName.substring(1));
						if (istack > 0) {
							if (strictNesting) {
								for (int j=1; j<istack; j++) {
									String t = (String) openTagType.pop();
									Console.println ("Error in SGML read:  unclosed " + t + " tag.");
									openTagPosn.pop();
									openTagFeatureSet.pop();
								}
							}
							int iv = openTagType.size() - istack;
							annotationType = openTagType.remove(iv);
							annotationStart = openTagPosn.remove(iv).intValue();
							annotationFS = openTagFeatureSet.remove(iv);
							int annotationEnd = doc.length();
							Annotation a = new Annotation (annotationType,
														   new Span(annotationStart, annotationEnd),
														   annotationFS);
							doc.addAnnotation(a);
							newAnnotations.add(a);
							state = COLLECTING_TEXT;
							// System.out.println("Annotation " + annotationType +
							//                    " [" + annotationStart + "-" + annotationEnd + "]"
							//                    + annotationFS + " added.");
						} else {
							Console.println ("Error in SGML read:  unmatched " + tagName +
											 " tag at position " + i);
							state = COLLECTING_TEXT;
						}
					} else {
						doc.append('<' + whitespaceBeforeType.toString() + type.toString() + '>');
						state = COLLECTING_TEXT;
					}
				}
				else // collecting type
					type.append(c);
			}
			else if (state == COLLECTING_FEATURE) {
				if (c == '=') {
					state = COLLECTING_VALUE;
					valueDelimiter = ' ';
				} else if (Character.isWhitespace(c)) {
					// skip whitespace
				} else if (c == '>') {
					if (feature.length() > 0)
						Console.println ("Error in SGML read:  in tag " + tagName +
										 ", feature " + feature + " not followed by value");
					endOfOpenTag (doc, tagName, fs);
					state = COLLECTING_TEXT;
				} else if (c == '/') {
					if (feature.length() > 0)
						Console.println ("Error in SGML read:  in tag " + tagName +
										 ", feature " + feature + " not followed by value");
					state = COLLECTING_TAG_END;      			                 
				} else {
					feature.append(c);
				}
			} else if (state == COLLECTING_VALUE) {
				if (value.length() == 0 && valueDelimiter == ' ' && (c == '"' || c == '\'')) {
					valueDelimiter = c;
				} else if (valueDelimiter == ' ' && c == '>') {
					fs.put(feature.toString().intern(), value.toString().intern());
					endOfOpenTag (doc, tagName, fs);
					state = COLLECTING_TEXT;
				} else if ((valueDelimiter == ' ' && Character.isWhitespace(c)) ||
						   (valueDelimiter != ' ' && c == valueDelimiter)) {
					Object jetValue = decodeJetValue(value.toString());
					fs.put(feature.toString().intern(), jetValue);
					feature = new StringBuffer();
					value = new StringBuffer();
					state = COLLECTING_FEATURE;
				} else if (c == '\\') {
					state = COLLECTING_ESCAPED_VALUE;
				} else {
					value.append(c);
				}
			} else if (state == COLLECTING_ESCAPED_VALUE) {
				value.append(c);
				state = COLLECTING_VALUE;
			} else if (state == COLLECTING_TAG_END) {
				if (Character.isWhitespace(c)) {
					// skip whitespace
				} else if (c == '>') {
					// close empty element tag
					Annotation a = new Annotation (tagName,
												   new Span(doc.length(), doc.length()),
												   fs);
					doc.addAnnotation(a);
					newAnnotations.add(a);
					state = COLLECTING_TEXT;
				} else {
					Console.println ("Error in SGML read:  in tag " + tagName +
									 " missing > after /");
					state = COLLECTING_TEXT;
				}
			} else if (state == SKIPPING_XML_DECLARATION) {
				if (c == '>')
					state = COLLECTING_TEXT;
			} else {
				Console.println ("Internal error in SGMLProcessor: invalid state");
				System.exit(1);
			}
		}
		if (state != COLLECTING_TEXT) {
			Console.println ("Error in SGML read:  incomplete tag");
		}
		if (!openTagType.empty()) {
			Console.println ("Error in SGML read:  unbalanced tags");
			while (!openTagType.empty()) {
				Console.println ("  Unclosed tag " + openTagType.pop());
				Console.println (" " + openTagPosn.pop());
				Console.println (" " + openTagFeatureSet.pop());
			}
		}
		/* if (includeWhitespace)
		   stretchAnnotations (doc, newAnnotations); */
		dereference (doc);
		return doc;
	}

	private static void endOfOpenTag (Document doc, String tagName, FeatureSet fs) {
		if (emptyTag (tagName)) {
			Annotation a = new Annotation (tagName,
										   new Span(doc.length(), doc.length()),
										   fs);
			doc.addAnnotation(a);
			newAnnotations.add(a);
		} else {
			openTagType.push(tagName);
			openTagPosn.push(new Integer(doc.length()));
			openTagFeatureSet.push(fs);
		}
	}

	private static Object decodeJetValue (String s) {
		if (s.startsWith("{") && s.endsWith("}")) {
			StringTokenizer st = new StringTokenizer(s.substring(1,s.length()-1));
			int len = st.countTokens();
			Object[] result = new Object[len];
			for (int i = 0; i < len; i++) {
				result[i] = st.nextToken().intern();
			}
			return result;
		} else if (s.startsWith("[") && s.endsWith("]")) {
			StreamTokenizer st = new StreamTokenizer(
													 new StringReader(s.substring(1,s.length())));
			try {
				FeatureSet fs = new FeatureSet(st, false, ']');
				return fs;
			} catch (IOException e) {
				System.out.println ("SGMLProcessor:  error in reading featureSet, " + s + "\n" + e);
				return null;
			} catch (PatternSyntaxError e) {
				System.out.println ("SGMLProcessor:  error in reading featureSet, " + s + "\n" + e);
				return null;
			}
		} else {
			return s.intern();
		}
	}

	// returns true if 'tag' is on list 'tags'.

	private static boolean tagToCapture (String tag, String[] tags) {
		if (allTags) return true;
		for (int j = 0; j < tags.length; j++) {
			if (tag.equalsIgnoreCase(tags[j])) {
				return true;
			}
		}
		return false;
	}

	// returns true if 'tag' is an empty tag (one with no corresponding close tag)

	private static boolean emptyTag (String tag) {
		if (emptyTags == null)
			return false;
		for (int j = 0; j < emptyTags.length; j++) {
			if (tag.equalsIgnoreCase(emptyTags[j])) {
				return true;
			}
		}
		return false;
	}

	static HashMap<String, Annotation> idToAnnotation;

	/**
	 *  convert all references to Annotations appearing as features of
	 *  other annotations from their string form ("#nnnn", where nnnn
	 *  is the id of the Annotation being references) to actual pointers
	 *  to Annotations.
	 */

	public static void dereference (Document doc) {
		idToAnnotation = new HashMap<String, Annotation>();
		String[] types = doc.getAnnotationTypes();
		for (int itype=0; itype<types.length; itype++) {
			Vector anns = doc.annotationsOfType(types[itype]);
			for (int i = 0; i < anns.size(); i++) {
				Annotation ann = (Annotation) anns.get(i);
				String id = (String) ann.get("id");
				if (id != null)
					idToAnnotation.put(id, ann);
			}
		}
		for (int itype=0; itype<types.length; itype++) {
			Vector anns = doc.annotationsOfType(types[itype]);
			for (int i = 0; i < anns.size(); i++) {
				Annotation ann = (Annotation) anns.get(i);
				FeatureSet fs = ann.attributes();
				for (Enumeration e = fs.keys() ; e.hasMoreElements() ;) {
					String feature = (String) e.nextElement();
					Object value = fs.get(feature);
					if (isAnnotationReference(value)) {
						fs.put(feature, resolveAnnotationReference(value));
					} else if (value instanceof Object[]) {
						Object[] ray = (Object[]) value;
						boolean arrayOfAnnotations = true;
						for (int j=0; j < ray.length; j++) {
							if (isAnnotationReference(ray[j])) {
								ray[j] = resolveAnnotationReference(ray[j]);
							} else {
								arrayOfAnnotations = false;
							}
						}
						if (arrayOfAnnotations) {
							Annotation[] r = new Annotation[ray.length];
							for (int j=0; j < ray.length; j++) {
								r[j] = (Annotation) ray[j];
							}
							fs.put(feature, r);
						}
					}
				}
			}
		}
	}

	private static boolean isAnnotationReference (Object value) {
		return (value instanceof String) &&
			((String) value).length() > 0 &&
			((String) value).charAt(0) == '#';
	}

	private static Object resolveAnnotationReference (Object value) {
		String id = ((String)value).substring(1);
		Annotation ann = (Annotation) idToAnnotation.get(id);
		if (ann == null) {
			System.out.println ("Undefined annotation reference " + value);
			return null;
		} else {
			return ann;
		}
	}

	/**
	 *  for each Annotation in 'newAnnotations', extends its end point to include
	 *  all whitespace following the annotated text, thus conforming to the Jet
	 *  standard for annotation.  However, an annotation is not extended past the
	 *  starting point of another annotation, so that proper nesting of annotations
	 *  is retained.
	 */

	private static void stretchAnnotations (Document doc, ArrayList newAnnotations) {
		int length = doc.length();
		boolean[] startingPoint = new boolean[length];
		for (int i = 0; i < newAnnotations.size(); i++) {
			Annotation a = (Annotation) newAnnotations.get(i);
			startingPoint[a.start()] = true;
		}
		for (int i = 0; i < newAnnotations.size(); i++) {
			Annotation a = (Annotation) newAnnotations.get(i);
			// annotations must be removed and re-added so that they are properly
			// indexed on end position
			doc.removeAnnotation(a);
			Span s = a.span();
			int posn = s.end();
			while (posn < length && !startingPoint[posn] && Character.isWhitespace(doc.charAt(posn)))
				posn++;
			s.setEnd(posn);
			doc.addAnnotation(a);
		}
  }


  /*
  public static void main (String[] args) {
	Document doc = sgmlToDoc ("Text <enamex attrb=\"value\" status=\"opt\"> Fred Smith</enamex> end.","enamex");
	View view = new View (doc, 1);
	}
  */

}
