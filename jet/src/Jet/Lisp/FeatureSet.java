// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Lisp;

import java.util.*;
import java.io.StreamTokenizer;
import java.io.IOException;
import Jet.Pat.PatternSyntaxError;
import Jet.Pat.FeatureTest;
import Jet.Tipster.Annotation;

/**
 * A FeatureSet provides a mapping from interned Strings to Objects. It is
 * intended to provide an efficient implementation for small numbers of
 * features.
 */

public class FeatureSet {

	protected Vector features;

	protected Vector values;

	/**
	 * Creates an empty FeatureSet.
	 */

	public FeatureSet() {
		features = new Vector(1);
		values = new Vector(1);
	}

	/**
	 * Creates a FeatureSet with one feature/value pair.
	 */

	public FeatureSet(String feat1, Object val1) {
		features = new Vector(1);
		values = new Vector(1);
		features.addElement(feat1);
		values.addElement(val1);
	}

	/**
	 * Creates a FeatureSet with two feature/value pairs.
	 */

	public FeatureSet(String feat1, Object val1, String feat2, Object val2) {
		features = new Vector(2);
		values = new Vector(2);
		features.addElement(feat1);
		values.addElement(val1);
		features.addElement(feat2);
		values.addElement(val2);
	}

	/**
	 * Creates a FeatureSet with three feature/value pairs.
	 */

	public FeatureSet(String feat1, Object val1, String feat2, Object val2, String feat3,
			Object val3) {
		features = new Vector(2);
		values = new Vector(2);
		features.addElement(feat1);
		values.addElement(val1);
		features.addElement(feat2);
		values.addElement(val2);
		features.addElement(feat3);
		values.addElement(val3);
	}

	/**
	 * creates a new FeatureSet with the same features and values as <CODE>fs</CODE>.
	 */

	public FeatureSet(FeatureSet fs) {
		int len = fs.size();
		features = new Vector(len);
		values = new Vector(len);
		this.putAll(fs);
	}

	/**
	 * This constructor reads a feature set from StreamTokenizer tok. The input
	 * should have the general form <BR>
	 * feature = value feature = value ... ] <BR>
	 * where the value may be an integer, a symbol, or a string; if parameter
	 * <I>allowVariables</I> is true, the value may also be a variable. The
	 * FeatureSet ends in token <I>endChar</I> ("]" in the example above).
	 * Feature-value pairs may be optionally separated by commas.
	 * 
	 * On entry, the first feature should be the next token to be read from
	 * <I>tok</I> (not the current token).
	 * 
	 */

	public FeatureSet(StreamTokenizer tok, boolean allowVariables, char endChar)
			throws IOException, PatternSyntaxError {
		features = new Vector();
		values = new Vector();
		tok.wordChars('_', '_');
		tok.wordChars('-', '-'); // <<< PATCH MAY 17
		tok.wordChars('.', '.');
		while (tok.nextToken() != endChar) {
			// skip comma, except at beginning of feature set
			if (tok.ttype == ',' & features.size() > 0)
				tok.nextToken();
			// get feature name
			if (tok.ttype != StreamTokenizer.TT_WORD)
				throw new PatternSyntaxError("feature name or " + endChar + " expected, " + tok
						+ " found");
			String feature = tok.sval.intern();
			if (tok.nextToken() == '=') {
				// get value: symbol, variable, string, or number
				if (tok.nextToken() == StreamTokenizer.TT_WORD) {
					String value = tok.sval.intern();
					if (Character.isUpperCase(value.charAt(0))) {
						if (allowVariables) {
							this.put(feature, new Variable(value));
						} else {
							this.put(feature, value); // <<< PATCH May 19
							// throw new PatternSyntaxError
							// ("Capitalized name (variable) not allowed [\"" +
							// value + "\"]");
						}
					} else {
						if (value == "null")
							this.put(feature, null);
						else
							this.put(feature, value);
					}
				} else if (tok.ttype == '"') {
					String value = tok.sval.intern();
					// this.put(feature,new Literal(value)); -- changed 30 Mar
					// 03
					this.put(feature, value);
				} else if (tok.ttype == StreamTokenizer.TT_NUMBER) {
					Integer value = new Integer((int) tok.nval);
					this.put(feature, value);
				} else if (tok.ttype == '[') {
					FeatureSet value = new FeatureSet(tok, allowVariables, ']');
					this.put(feature, value);
				} else {
					throw new PatternSyntaxError("feature value expected");
				}
			} else if (allowVariables && tok.ttype == '?') {
				FeatureTest value = new FeatureTest(tok);
				this.put(feature, value);
			} else
				throw new PatternSyntaxError("= or ? expected");
		}
	}

	/**
	 * Associates the specified value <I>val</I> with the feature <I>feat</I>.
	 */

	public void put(String feat, Object val) {
		int i = features.indexOf(feat);
		if (i < 0) {
			features.addElement(feat);
			values.addElement(val);
		} else
			values.set(i, val);
	}

	/**
	 * putAll adds all the feature/value pairs in FeatureSet fs
	 */

	public void putAll(FeatureSet fs) {
		if (fs == null)
			return;
		Enumeration features = fs.keys();
		while (features.hasMoreElements()) {
			String feature = (String) features.nextElement();
			Object value = fs.get(feature);
			this.put(feature, value);
		}
	}

	/**
	 * Returns the value associated with feature <I>feat</I>.
	 */

	public Object get(String feat) {
		int i = features.indexOf(feat);
		if (i < 0)
			return null;
		else
			return values.get(i);
	}
	
	/**
	 * Removes the value associated with feature <I>feat</I>.
	 */
	
	public void remove(String feat) {
		int i= features.indexOf(feat);
		if (i >= 0) {
			features.remove(i);
			values.remove(i);
		}
	}

	/**
	 * Returns true if the FeatureSet has feature <I>feat</I> with some value,
	 * possibly <CODE>null</CODE>.
	 */

	public boolean containsFeature(String feat) {
		return features.indexOf(feat) >= 0;
	}

	/**
	 * returns true if the FeatureSet is contained in <CODE>fs</CODE>: if
	 * every feature in the FeatureSet is present in <CODE>fs</CODE> with the
	 * same value.
	 */

	public boolean subsetOf(FeatureSet fs) {
		if (fs == null)
			return false;
		int len = features.size();
		for (int i = 0; i < len; i++) {
			String feat = (String) features.get(i);
			if (!fs.containsFeature(feat))
				return false;
			if (!values.get(i).equals(fs.get(feat)))
				return false;
		}
		return true;
	}

	/**
	 * Returns true if the FeatureSet is equal to <I>fs</I>: whether the two
	 * FeatureSets have the same features with equal values.
	 */

	public boolean equals(FeatureSet fs) {
		if (fs == null)
			return false;
		int len = features.size();
		if (len != fs.size())
			return false;
		for (int i = 0; i < len; i++) {
			String feat = (String) features.get(i);
			if (!fs.containsFeature(feat))
				return false;
			if (!values.get(i).equals(fs.get(feat)))
				return false;
		}
		return true;
	}

	/**
	 * Returns true if the FeatureSet is equal to <I>fs</I>: whether the two
	 * FeatureSets have the same features with equal values.
	 */
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof FeatureSet)) {
			return false;
		}
		return this.equals((FeatureSet) obj);
	}

	/**
	 * Returns an Enumeration over the features in the feature set.
	 */

	public Enumeration keys() {
		return features.elements();
	}

	/**
	 * returns the number of features in the FeatureSet.
	 */

	public int size() {
		return features.size();
	}

	/**
	 * Returns a new feature set in which every value of the original feature
	 * set which is a variable has been replace by the binding of that variable
	 * in <I>bindings</I>.
	 */

	public FeatureSet substitute(HashMap bindings) {
		FeatureSet fs = new FeatureSet();
		int len = features.size();
		for (int i = 0; i < len; i++) {
			Object value = values.get(i);
			if (value instanceof Variable) {
				value = bindings.get(((Variable) value).name);
			} else if (value instanceof FeatureSet) {
				value = ((FeatureSet) value).substitute(bindings);
			}
			fs.put((String) features.get(i), value);
		}
		return fs;
	}

	/**
	 * If every feature which occurs in both FeatureSets <I>a</I> and <I>b</I>
	 * has the same value in both FeatureSets, returns a new FeatureSet with the
	 * union of the features; otherwise returns <B>null</B>.
	 */

	public static FeatureSet unify(FeatureSet a, FeatureSet b) {
		FeatureSet result = new FeatureSet(a);
		if (b == null)
			return result;
		Enumeration features = b.keys();
		while (features.hasMoreElements()) {
			String feature = (String) features.nextElement();
			Object aValue = a.get(feature);
			Object bValue = b.get(feature);
			if (aValue == null)
				result.put(feature, bValue);
			else if (aValue != bValue)
				return null;
		}
		return result;
	}

	/**
	 * returns a printable form of the feature set, of the form <BR>
	 * [feature1=value1 feature2=value2 ...]. <BR>
	 * Inverse features, ending in "-1", are suppressed to avoid infinite loops.
	 */

	public String toString() {
		return "[" + toSGMLString() + "]";
	}

	/**
	 * returns a string representation of the feature set, without enclosing
	 * braces, of the form <BR>
	 * feature1=value1 feature2=value2 ...<BR>
	 * which is suitable when the feature set is part of an annotation or SGML
	 * tag. Inverse features, ending in "-1", are suppressed to avoid infinite
	 * loops.
	 */

	public String toSGMLString() {
		return toSGMLString(Annotation.MAX_ANNOTATION_DEPTH, false, false);
	}

	/**
	 * returns a string representation of the feature set, without enclosing
	 * braces, of the form <BR>
	 * feature1=value1 feature2=value2 ...<BR>
	 * which is suitable when the feature set is part of an annotation or SGML
	 * tag. Inverse features, ending in "-1", are suppressed to avoid infinite
	 * loops.
	 * 
	 * @param nestingLimit
	 *            limit of depth of nested Annotations which will be represented
	 *            in the String.
	 * @param useIds
	 *            if true, references to Annotations which are nested more
	 *            deeply than 'nestingLimit' are rendered as '#nnn', where 'nnn'
	 *            is the 'id' feature of the referenced Annotation; if false,
	 *            these references are rendered as "..."
	 * @param quoteValues
	 *            if true, values of features are enclosed in double quotes, as
	 *            required by XML. Quotes inside quoted values are escaped
	 *            (preceded by a backslash).
	 */

	public String toSGMLString(int nestingLimit, boolean useIds, boolean quoteValues) {
		int len = features.size();
		String result = "";
		for (int i = 0; i < len; i++) {
			if (((String) features.elementAt(i)).endsWith("-1"))
				continue;
			if (i > 0)
				result += " ";
			if (quoteValues) {
				result += features.elementAt(i)
						+ "="
						+ "\""
						+ valueToString(values.elementAt(i), nestingLimit, useIds, quoteValues)
								.replaceAll("\"", "\\\\\"") + "\"";
			} else {
				result += features.elementAt(i) + "="
						+ valueToString(values.elementAt(i), nestingLimit, useIds, quoteValues);
			}
		}
		return result;
	}

	private static String valueToString(Object value, int nestingLimit, boolean useIds,
			boolean quoteValues) {
		if (value == null) {
			return "null";
		} else if (value instanceof Object[]) {
			// value is array -- write out elements enclosed in { ... }
			StringBuffer sb = new StringBuffer("{");
			Object[] array = (Object[]) value;
			for (int i = 0; i < array.length; i++) {
				if (i > 0)
					sb.append(" ");
				sb.append(valueToString(array[i], nestingLimit, useIds, quoteValues));
			}
			sb.append("}");
			return sb.toString();
		} else if (value instanceof Annotation) {
			Annotation ann = (Annotation) value;
			if (nestingLimit > 1) {
				return ann.toSGMLString(nestingLimit - 1, useIds, quoteValues);
			} else if (useIds) {
				return "#" + ann.getId();
			} else {
				return "...";
			}
		} else if (value instanceof FeatureSet) {
			return ((FeatureSet) value).embeddedFStoSGMLString(nestingLimit, useIds, quoteValues);
		} else {
			return value.toString();
		}
	}

	private String embeddedFStoSGMLString(int nestingLimit, boolean useIds, boolean quoteValues) {
		int len = features.size();
		String result = "[";
		for (int i = 0; i < len; i++) {
			if (((String) features.elementAt(i)).endsWith("-1"))
				continue;
			if (i > 0)
				result += " ";
			String value = valueToString(values.elementAt(i), nestingLimit, useIds, quoteValues);
			if (quoteValues && !value.matches("\\A[a-zA-Z]*\\z")) {
				result += features.elementAt(i) + "=" + "\"" + value.replaceAll("\"", "\\\\\"")
						+ "\"";
			} else {
				result += features.elementAt(i) + "=" + value;
			}
		}
		result += "]";
		return result;
	}

	/**
	 * add an 'id' feature to all Annotations referenced by this FeatureSet.
	 * This is invoked prior to converting this FeatureSet to a String, if
	 * annotation references are to be converted to '#nnn' form.
	 */

	public void prepareToMakeString(int nestingLimit) {
		int len = features.size();
		String result = "";
		for (int i = 0; i < len; i++) {
			prepareToMakeString(values.elementAt(i), nestingLimit);
		}
	}

	private void prepareToMakeString(Object value, int nestingLimit) {
		if (value != null) {
			if (value instanceof Object[]) {
				Object[] array = (Object[]) value;
				for (int j = 0; j < array.length; j++) {
					prepareToMakeString(array[j], nestingLimit);
				}
			} else if (value instanceof Annotation) {
				Annotation ann = (Annotation) value;
				if (nestingLimit > 1) {
					ann.prepareToMakeString(nestingLimit - 1);
				} else {
					ann.assignId();
				}
			}
		}
	}

}
