// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Refres;

import java.util.*;
import Jet.Tipster.*;
import Jet.Parser.ParseTreeNode;

/**
 *  functions for computing 'Hobbs distance' for reference resolution.
 */

public class Hobbs {

  /**
   *  if true, print the potential antecedents of each anaphor as found during
   *  the Hobbs' search performed by collectAntecedents.
   */

	static private boolean hobbsTrace = false;

	/**
	 *  value returned by distance method if antecedent is in same sentence but
	 *  is not syntactically allowed.
	 */

	static final int SAME_SENTENCE = 9998;

	/**
	 *  computes the distance (number of mention nodes traversed) in a Hobbs search
	 *  starting from parse tree node 'm2' and searching backwards for parse
	 *  tree node 'm1'.
	 *
	 *  @param   m1          a parse tree node (the potential antecedent)
	 *  @param   m2          a parse tree node (the anaphor)
	 *  @param   antecedents a list of the potential antecedents in the sentence
	 *                       containing m2, in Hobbs order, as produced by
	 *                       collectAntecedents
	 *  @param   sentences   a Vector of the sentences in the Document
	 */

	public static int distance (Document doc, Annotation m1, Annotation m2,
	                            ArrayList<Annotation> antecedents, Vector sentences) {
		Span span1 = m1.span();
		Span span2 = m2.span();
		// determine associated sentences for nodes m1 and m2
		int isent1 = -1;
		int isent2 = -1;
		Annotation sent1 = null;
		Annotation sent2 = null;
		for (int i = 0; i < sentences.size(); i++) {
			Annotation sentence = (Annotation) sentences.get(i);
			Span span = sentence.span();
			if (span1.within(span)) {
				isent1 = i;
				sent1 = sentence;
			}
			if (span2.within(span)) {
				isent2 = i;
				sent2 = sentence;
			}
		}
		if (isent1 < 0 || isent2 < 0) {
			System.out.println ("Hobbs.distance:  cannot find containing sentences.");
			return 99999;
		}
		Annotation parse1 = (Annotation) sent1.get("parse");
		Annotation parse2 = (Annotation) sent2.get("parse");
		// in same sentence
		if (isent1 == isent2) {
			int distance;
			if (parse2 != null) {
				distance = antecedents.indexOf(m1);
				if (distance < 0) {
				// not a syntactically valid antecedent
					return SAME_SENTENCE;
				}
			} else {
				distance = mentionCount(doc, m1.start(), m2.start());
			}
			return distance;
		} else {
			// compute distance from root of s1 to m1
			int d1;
			if (parse1 != null) {
				d1 = bfsCount(parse1, m1);
			} else {
				d1 = mentionCount(doc, sent1.start(), m1.start());
			}
			// count mentions in intermediate sentences
			int d2 = 0;
			for (int i=isent1+1; i<isent2; i++) {
				Annotation sentence = (Annotation) sentences.get(i);
				Annotation parse = (Annotation) sentence.get("parse");
				if (parse != null) {
					d2 += bfsCount (parse, null);
				} else {
					d2 += mentionCount (doc, sentence.start(), sentence.end());
				}
			}
			// compute distance from root of lastSentence to m2
			int d3;
			if (parse2 != null) {
				d3 = antecedents.size();
			} else {
				d3 = mentionCount(doc, sent2.start(), m2.start());
			}
			// return sum
			return d1 + d2 + d3;
		}
	}

	/**
	 *  returns a count of the mentions encountered in a breadth-first
	 *  traversal of the tree rooted at 'root', up to the node 'target'.
	 *  If 'target' is null, returns a count of all the mentions in
	 *  the tree.
	 */

	private static int bfsCount (Annotation root, Annotation target) {
		LinkedList q = new LinkedList();
		int count = 0;
		q.add(root);
		while (q.size() > 0) {
			Annotation a = (Annotation) q.removeFirst();
			if (a == target)
				return count;
			else {
				if (hobbsAntecedent(a))
					count++;
			}
			Annotation[] children = ParseTreeNode.children(a);
			if (children != null)
				for (int ichild=0; ichild<children.length; ichild++) {
					if (children[ichild] != null)
						q.add(children[ichild]);
				}
		}
		if (target != null) {
			System.out.println ("Hobbs.bfs:  target (" + target +
			                    ") not underneath root (" + root + ").");
		}
		return count;
	}

	static int mentionCount (Document doc, int start, int end) {
		int count = 0;
		for (int posn=start; posn<end; posn++) {
			Vector anns = doc.annotationsAt(posn, "constit");
			if (anns != null) {
				for (int i=0; i<anns.size(); i++) {
					Annotation ann = (Annotation) anns.get(i);
					if (ann.get("mention") != null) {
						count++;
					}
				}
			}
		}
		return count;
	}

	/**
	 *  computes the Hobbs distance of all syntactically valid antecedents
	 *  in the same sentence of <CODE>node</CODE>.  It does this by
	 *  traversing the tree in Hobbs order and accumulating a list of mentions.
	 *
	 *  @param  node     the node (generally, NP) of the anaphor
	 *                   (a constit annotation)
	 *  @param  parents  a map from each node in the parse tree to its parent
	 *  @param  doc      the document containing the sentence
	 *
	 *  @return an ArrayList containing all the mentions in the sentence which
	 *          are syntactically valid antecedents of <CODE>node</CODE>.  The
	 *          first element is the closest mention (Hobbs distance 1), the
	 *          second element is at Hobbs distance 2, etc.
	 */

	static ArrayList<Annotation> collectAntecedents
			(Annotation node, HashMap<Annotation, Annotation> parents,
			                  Document doc) {
	  if (hobbsTrace)
	  	System.out.println ("Collecting antecedents of " + doc.text(node));
	  ArrayList<Annotation> antecedents = new ArrayList<Annotation>();
	  ArrayList<Annotation> path = new ArrayList<Annotation>();
	  // steps from Hobbs' "Resolving Pronoun References" paper
	  // step 1
	  //   implicit:  we are already at NP node dominating anaphor
	  // step 2 - go up tree to first NP or S encountered, call it X
	  Annotation x = ascendToNpOrS(node, path, parents);
	  if (x == null) return antecedents;
	  // step 3 - traverse tree below X to the left of path P in
	  //          a left-to-right breadth-first fashion
	  bfs (x, antecedents, path, node, doc, true, parents);
		while (true) {
			// step 4 & 5
			x = ascendToNpOrS(x, path, parents);
			if (x == null) break;
			// step 7
			bfs (x, antecedents, path, node, doc, false, parents);
		}
		return antecedents;
	}

	/**
	 *  perform breadth-first search below <CODE>root</CODE> to collect
	 *  potential antecedents as part of Hobbs' search.
	 */

	private static void bfs (Annotation root,
	                         ArrayList<Annotation> antecedents,
	                         ArrayList<Annotation> path,
	                         Annotation anaphor,
	                         Document doc,
	                         boolean requireInterveningNPS,
	                         HashMap<Annotation, Annotation> parents) {
		LinkedList<Annotation> q = new LinkedList<Annotation>();
		q.add(root);
		while (!q.isEmpty()) {
			Annotation a = q.removeFirst();
			if (!path.contains(a) &&
			    hobbsAntecedent(a) &&
			    !antecedents.contains(a) &&
			    (!requireInterveningNPS || interveningNPS(root, a, parents))) {
				antecedents.add(a);
				if (hobbsTrace)
					System.out.println ("    antecedent: " + doc.text(a));
			}
			// do not look below anaphor
			if (a == anaphor) continue;
			// add children to left of (or on) path
			Annotation[] children = ParseTreeNode.children(a);
			if (children != null) {
				for (Annotation child : children) {
					if (child != null) {
						q.add(child);
						if (path.contains(child)) break;
					}
				}
			}
		}
	}

	/**
	 *  returns true if there an NP or S intervening between nodes x and y.
	 *
	 *  @param  x        a parse tree node (a constit Annotation)
	 *  @param  y        a parse tree node which should be an ancestor of x
	 *  @param  parents  a map from parse tree nodes to their parents
	 */

	private static boolean interveningNPS(Annotation x, Annotation y,
	                                      HashMap<Annotation, Annotation> parents) {
	  if (y == x)
	  	return false;
		while (true) {
			y = parents.get(y);
			if (y == null) return false;
			if (y == x) return false;
			if (y.get("cat") == "np" && y.get("cat") != "s") return true;
		}
	}

	private static Annotation ascendToNpOrS (Annotation node,
	                                         ArrayList<Annotation> path,
	                                         HashMap<Annotation, Annotation> parents) {
		path.add(node);
		Annotation x = parents.get(node);
		while (x != null && x.get("cat") != "np" && x.get("cat") != "s") {
			path.add(x);
			x = parents.get(x);
		}
		return x;
	}

	private static boolean hobbsAntecedent (Annotation node) {
		return node != null && node.get("mention") != null;
	}

	/**
	 *  returns true if parse tree nodes <CODE>x</CODE> and <CODE>y</CODE>
	 *  are part of the same simplex sentence (used for reflexive pronoun tests).
	 */

	public static boolean sameSimplex (Annotation x, Annotation y,
	                                   HashMap<Annotation, Annotation> parents) {
		while (x != null && x.get("cat") != "s")
			x = parents.get(x);
		while (y != null && y.get("cat") != "s")
			y = parents.get(y);
		return x != null && x == y;
	}
}
