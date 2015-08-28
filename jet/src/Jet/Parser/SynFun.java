// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2004, 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

import Jet.Lisp.FeatureSet;
import Jet.Tipster.*;
import Jet.Refres.Resolve;
import java.util.HashMap;

/**
 *  syntactic functions which retrieve information from parse trees.
 *  In general, these should work with trees produced either by
 *  the full-sentence (Penn Tree Bank) parser or the partial parser.
 */

public class SynFun {

	/**
	 *  returns the head string for the current node.  If the 'pa'
	 *  feature has a 'head' feature, return that value (plus the
	 *  particle, if present as a 'part' feature).  If the 'pa'
	 *  feature is a string, return that string.  If 'node' does
	 *  not have a 'pa' feature, return null.
	 */

	public static String getImmediateHead (Annotation node) {
		Object pa = node.get("pa");
		if (pa == null) {
			return null;
		} else {
			return headOfPa(pa, node);
		}
	}

	/**
	 *  returns the head string from the value of the 'pa' feature.  If the 'pa'
	 *  feature has a 'head' feature, return that value (plus the
	 *  particle, if present as a 'part' feature).  If the 'pa'
	 *  feature is a string, return that string.  Otherwise there
	 *  is an error, return "?".
	 *
	 *  @param pa   value of pa feature
	 *  @param ann  annotation of which this is the pa feature;  used only
	 *              for error messages
	 */

	public static String headOfPa (Object pa, Annotation ann) {
		if (pa instanceof FeatureSet) {
			FeatureSet pafs = (FeatureSet) pa;
			Object ohead = pafs.get("head");
			if (!(ohead instanceof String)) {
				System.out.println ("headOfPa:  unexpected pa attribute " + pa +
				                    " in annotation " + ann);
				return "?";
			}
			String head = (String) ohead;
			String particle = (String) pafs.get("part");
			if (particle != null)
				return (head + "-" + particle).intern();
			else
				return head.intern();
		} else if (pa instanceof String) {
			return ((String) pa).intern();
		} else {
			System.out.println ("headOfPa:  unexpected pa attribute " + pa +
				                  " in annotation " + ann);
			return "?";
		}
	}

  /**
   *  returns the 'pa' feature directly or indirectly associated with
   *  parse tree node 'constit'.	If 'constit' has a pa feature itself,
   *  return it;  otherwise follow the chain of 'headC' links down the
   *  parse tree until a node is found with a 'pa' feature.
   */

	public static Object getPA (Annotation constit) {
		Object pa;
		while (constit != null) {
			if ((pa = constit.get("pa")) != null)
				return pa;
			constit = (Annotation) constit.get("headC");
		}
		return null;
	}

	/**
	 *  returns the head string of constituent 'ann' in a parse tree.
	 *  If there is a 'pa' feature directly or indirectly associated with
	 *  the constituent, use that to determine the head.  Otherwise follow
	 *  the 'headC' chain and then concatenate (with '-') the tokens of
	 *  the constituent at the end of the chain.
	 */

	public static String getHead (Document doc, Annotation ann) {
		Object pa = getPA(ann);
		if (pa == null) {
			ann = Resolve.getHeadC(ann);
			return Resolve.normalizeName(doc.text(ann)).replace(' ','-').intern();
		} else {
			return headOfPa(pa, ann);
		}
	}

	/**
	 *  returns the name associated with a noun phrase, as a single
	 *  string, or null if the np does not have a name.  Whitespace
	 *  between tokens of a name is normalized to single blanks.
	 */

	public static String getName (Document doc, Annotation constit) {
		Annotation head = Resolve.getHeadC(constit);
		if (head.get("cat") != "name") return null;
		return Resolve.normalizeName(doc.text(head));
	}

	/**
	 *  if the head (the end of the 'headC' chain) of constituent 'ann'
	 *  is a name, return the name itself (with tokens connected by '-');
	 *  otherwise return the head as determined by 'getHead'.
	 */

	public static String getNameOrHead (Document doc, Annotation ann) {
		String name = getName(doc, ann);
		if (name != null)
			return Resolve.normalizeName(name).replace(' ','-');
		else
			return getHead (doc, ann);
	}

	/**
	 *  returns the determiner of 'constit', or null if the
	 *  consitutent has no determiner.  The determiner may appear either
	 *  as an attribute of the constituent, or as an attribute of the pa
	 *  feature of the constituent.
	 */

	public static String getDet (Annotation constit) {
		String det = (String) constit.get("det");
		if (det != null)
			return det;
		Object paObj = constit.get("pa");
		if (!(paObj instanceof FeatureSet)) return null;
		FeatureSet pa = (FeatureSet) paObj;
		if (pa == null)
			return null;
		if (pa.get("det") instanceof String)
			return (String) pa.get("det");
		else
			return null;
	}

		/**
	 *  returns the number feature of noun phrase 'constit'
	 *  (singular or plural), or 'null' if the number feature is
	 *  not specified.
	 */

	public static String getNumber (Annotation constit) {
		Object pa = getPA(constit);
		if (pa == null) {
			return null;
		} else if (pa instanceof FeatureSet) {
			String number = (String) ((FeatureSet)pa).get("number");
			if (number == null)
				return null;
			else
				return number.intern();  // not sure if necessary
		} else {
			return null;
		}
	}

	/**
	 *  returns true if noun phrase 'constit' has a human head,
	 *  as recorded either an a 'human' feature on PA (by the
	 *  chunk patterns) or an 'nhuman' feature in the dictionary.
	 */

	public static boolean getHuman (Annotation constit) {
		Object pa = SynFun.getPA(constit);
		if ((pa != null) && (pa instanceof FeatureSet) &&
		       (((FeatureSet)pa).get("human") != null))
			return true;
		Annotation headC = Resolve.getHeadC(constit);
		return headC.get("nhuman") != null;
	}

	/**
	 *  returns a map from each child node to its parent in the parse tree
	 *  rooted at <CODE>root</CODE>.
	 */

	public static HashMap<Annotation, Annotation> collectParents (Annotation root) {
		HashMap<Annotation, Annotation> parents =
			new HashMap<Annotation, Annotation>();
		collectParents (root, parents);
		return parents;
	}

	private static void collectParents (Annotation node, HashMap<Annotation, Annotation> parents) {
		if (node == null) return;
		Annotation[] children = ParseTreeNode.children(node);
		if (children == null)
			return;
		for (Annotation child : children) {
			if (child != null) {
				parents.put(child, node);
				collectParents (child, parents);
			}
		}
	}
}
