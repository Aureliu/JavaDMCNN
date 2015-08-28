// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

import Jet.Tipster.*;
import Jet.Lisp.*;
import Jet.Refres.Resolve;
import Jet.Pat.Pat;

import java.util.*;

/**
 *  takes a Penn Tree Bank tree and adds links corresponding to
 *  various syntactic relations used by coreference.
 */

public class AddSyntacticRelations {

	/**
	 *  annotate the constituents of document 'doc' within Span 'span'.
	 *  If there is a 'sentence' annotation starting at the beginning
	 *  of 'span', annotate all nodes in the parse tree associated
	 *  with that sentence.  Otherwise annotate all nodes ('constit'
	 *  annotations) within 'span'.
	 */

	public static void annotate (Document doc, Span span) {
		// if there is a parse tree at the start of this span, annotate parse
		// tree top-down
		Vector sentences = doc.annotationsAt(span.start(), "sentence");
		if (sentences != null && sentences.size() > 0) {
			Annotation sentence = (Annotation) sentences.get(0);
			if (sentence.get("parse") instanceof Annotation) {
				Annotation parse = (Annotation) sentence.get("parse");
				if (parse != null) {
					annotateParseTree (doc, parse);
					return;
				}
			}
		}
		// otherwise iterate over all constituents in span
		Vector constits = doc.annotationsOfType("constit");
		if (constits == null) return;
		for (int i=0; i<constits.size(); i++) {
			Annotation constit = (Annotation) constits.get(i);
			if (constit.span().within(span))
				annotateConstit(doc, constit);
		}
	}

	/**
	 *  annotate all nodes in the parse tree rooted at 'node'.
	 */

	private static void annotateParseTree (Document doc, Annotation node) {
		// check for possible error in tree
		if (node == null) return;
		annotateConstit (doc, node);
		Annotation[] children = ParseTreeNode.children(node);
		if (children != null)
			for (int i=0; i<children.length; i++)
				annotateParseTree(doc, children[i]);
	}

	/**
	 *  annotate node 'constit'.
	 */

	private static void annotateConstit (Document doc, Annotation constit) {
		restructurePoss (doc, constit);
		restructureConj (doc, constit);
		// add subject / verb / object
		processS (doc, constit, null);
		processNP (doc, constit);
		// for each relation:  test & annotate
		addOfRelation (doc, constit);
		addPObjRelation (doc, constit);
		addDetRelation (doc, constit);
		addPreNameRelation (doc, constit);
		addAppositeRelation (doc, constit);
		addPredCompRelation (doc, constit);
		addConjRelation (doc, constit);
	}

	/**
	 *  restructurePoss modifies the parse tree to create a separate
	 *  structure for the NP without the POS in possessive modifiers,
	 *  so that (NP (DT the) (JJ young) (NN farmer) (POS 's)) becomes
	 *  (NP$ (NP  (DT the) (JJ young) (NN farmer)) (POS 's)).
	 */

	private static void restructurePoss (Document doc, Annotation constit) {
		if (constit.get("cat") != "np") return;
		Annotation[] children = ParseTreeNode.children(constit);
		// Added 11/22/04, MDW
		if (children.length <= 1) return;
		Annotation lastChild = children[children.length - 1];
		if (lastChild != null && lastChild.get("cat") == "pos") {
		// if (doc.text(lastChild).trim().equals("'s")) {
			// System.out.println ("Restructuring possessive");
			Annotation[] childrenExceptPos = new Annotation[children.length -1];
			for (int i=0; i < childrenExceptPos.length; i++)
				childrenExceptPos[i] = children[i];
			Annotation headInnerNP = childrenExceptPos[childrenExceptPos.length - 1];
			int endInnerNP = childrenExceptPos[childrenExceptPos.length - 1].end();
			Annotation innerNP = new Annotation ("constit",
			                                     new Span(constit.start(),
			                                              endInnerNP),
			                                     new FeatureSet(
			                                     	"cat", "np",
			                                     	"children", childrenExceptPos,
			                                     	"headC", headInnerNP));
			doc.addAnnotation(innerNP);
			children = new Annotation[2];
			children[0] = innerNP;
			children[1] = lastChild;
			constit.put("cat", "np$");
			constit.put("children", children);
		}
	}

	/**
	 *  restructure (np (a) (b) (cconj and) (c) (d)) into
	 *              (np (np (a) (b)) (cconj and) (np (c) (d)))
	 */

	private static void restructureConj (Document doc, Annotation constit) {
		if (constit.get("cat") != "np") return;
		// verify that this is lowest-level np
		Annotation headC = (Annotation) constit.get("headC");
		if (headC != null && headC.get("cat") == "np")
			return;
		// search for conjunction as child
		Annotation[] children = ParseTreeNode.children(constit);
		if (children.length < 3)
			return;
		int iconj = 0;
		for (int ichild=1; ichild<children.length-1; ichild++) {
			Annotation child = children[ichild];
			if (child == null) continue;
			if (child.get("cat") == "cconj") {
				iconj = ichild;
				break;
			}
		}
		// if no conjunction, return
		if (iconj == 0) return;
		// split into two np's
		Annotation[] leftChildren = new Annotation[iconj];
		for (int i=0; i<iconj; i++)
			leftChildren[i] = children[i];
		Annotation[] rightChildren = new Annotation[children.length-iconj-1];
		for (int i=iconj+1; i<children.length; i++)
			rightChildren[i-iconj-1] = children[i];
		Annotation conjunct = children[iconj];
		Annotation leftNP = new Annotation ("constit",
			                                     new Span(constit.start(),
			                                              conjunct.start()),
			                                     new FeatureSet(
			                                     	"cat", "np",
			                                     	"children", leftChildren,
			                                     	"headC", leftChildren[leftChildren.length-1]));
		doc.addAnnotation(leftNP);
		Annotation rightNP = new Annotation ("constit",
			                                     new Span(conjunct.end(),
			                                              constit.end()),
			                                     new FeatureSet(
			                                     	"cat", "np",
			                                     	"children", rightChildren,
			                                     	"headC", rightChildren[rightChildren.length-1]));
		doc.addAnnotation(rightNP);
		children = new Annotation[3];
		children[0] = leftNP;
		children[1] = conjunct;
		children[2] = rightNP;
		constit.put("children", children);
		constit.put("headC", rightNP);
		// System.out.println (">>Dividing conjoined np " + doc.text(constit));
		// System.out.println ("  Left np  " + doc.text(leftNP));
		// System.out.println ("  Right np " + doc.text(rightNP));
	}

	/**
	 *  add 'subject' and 'object' relations to S nodes, and add
	 *  the head of the verb as an attribute of the S node.  If the
	 *  S is a relative clause, 'head' is the head of that clause,
	 *  otherwise null.
	 *  July 04:  also add 'pp' relation if S is modified by a PP
	 */

	private static void processS (Document doc, Annotation node, Annotation head) {
		String cat = (String) node.get("cat");
		if (cat != "s") return;
		FeatureSet pa = (FeatureSet) node.get("pa");
		// if this S has already been processed as part of a higher
		// structure (e.g., relative clause), return
		if (pa != null) return;
		Annotation vp = assignVerbPa(doc, node, node);
		pa = (FeatureSet) node.get("pa");
		if (vp == null || pa == null) return;
		Annotation partC = childWithCat(vp, "prt");
		if (partC != null) {
			partC = Resolve.getHeadC(partC);
			if (partC.get("cat") == "dp") {
				Object part = partC.get("pa");
				if (part instanceof String) {
					pa.put("part", part);
				}
			}
		}
		// if no explicit subject, and we are in a relative clause,
		// use head of relative clause
		Annotation subject = nonTimeNP(node);
		if (subject == null && head != null)
			subject = head;
		Annotation object = nonTimeNP(vp);
		if (pa.get("voice") != "passive") {
			if (subject != null)
				node.put("subject", subject);
			if (object != null)
				node.put("object", object);
		} else {
			if (subject != null)
				node.put("object", subject);
		}
		Annotation pp = childWithCat(vp, "pp");
		if (pp != null) {
			node.put("pp", pp);
			String prep = getPreposition(doc, pp);
			if (prep != null && Resolve.in(prep, prepositions)) {
				Annotation ppObj = getPPObj(doc, pp);
				if (ppObj != null) {
					node.put(prep, ppObj);
				}
			}
		}
	}

	/**
	 *  if 'node' is an NP with a relative or reduced relative
	 *  modifier, add 'subject', 'object', and 'pa' attributes.
	 *  If a full relative clause, the attributes are added to the 's'
	 *  node;  if a reduced relative, to the 'vp' node.
	 */

	private static void processNP (Document doc, Annotation node) {
		if (node.get("cat") != "np") return;
		Annotation np2 = childWithCat(node, "np");
		Annotation vp = childWithCat (node, "vp");
		Annotation sbar = childWithCat (node, "sbar");
		// process reduced relative clause
		if (np2 != null && vp != null) {
			Annotation vp2 = processBeComplement (vp, vp, doc, null);
			FeatureSet pa = (FeatureSet) vp.get("pa");
			if (vp2 == null || pa == null) return;
			Annotation object = nonTimeNP(vp2);
			if (pa.get("voice") != "passive") {
				vp.put("subject", np2);
				if (object != null)
					vp.put("object", object);
			} else {
				vp.put("object", np2);
			}
			return;
		}
		// process full relative clause
		if (np2 != null && sbar != null) {
			Annotation whnp = childWithCat(sbar, "whnp");
			if (whnp != null) {
				whnp.put("host", node);
			}
			Annotation s = childWithCat(sbar, "s");
			if (s != null) {
				processS (doc, s, np2);
				return;
			}
		}
	}

	/**
	 *  analyzes the verbal structure under node 'node' within sentence
	 *  subtree 's' ('node' will either be equal to 's', or a vp under 's').
	 *  Assign to node 's' the feature pa with feature 'head' equal
	 *  to the root form of the main verb of the S, and feature 'voice'
	 *  with value 'passive' if a passive construct.  Looks through
	 *  progressive, perfect, and modal constructs to find the main verb.
	 *  Returns the VP node dominating the main verb. <P>
	 *  In addition, creates a <I>vg</I> (verb group) or <I>vg-pass</I>
	 *  constituent subsuming the modals, auxiliaries, and main verb.
	 */

	private static Annotation assignVerbPa (Document doc, Annotation node, Annotation s) {
		Annotation vp1 = childWithCat(node, "vp");
		if (vp1 == null) return null;
		Annotation to = childWithCat(vp1, "p");
		if (to != null)
			return assignVerbPa(doc, vp1, s);
		// get main verb
		Annotation mainV;
		mainV = childWithCat(vp1, "tv");
		if (mainV == null)
			mainV = childWithCat(vp1, "ving");
		if (mainV == null)
			mainV = childWithCat(vp1, "v");
		// Progressive and simple passive ...
		if (mainV != null && SynFun.getImmediateHead(mainV) == "be") {
			Annotation vp2 = childWithCat(vp1, "vp");
			if (vp2 != null) {
				Annotation z = processBeComplement (node, vp2, doc, vp1);
				if (z != null) {
					return z;
				}
			}
		}
		// Perfect (incl. perfect progressive and passive perfect) ...
		if (mainV != null && SynFun.getImmediateHead(mainV) == "have") {
			Annotation vp2 = childWithCat(vp1, "vp");
			if (vp2 != null) {
				Annotation ven= childWithCat(vp2, "ven");
				if (ven != null) {
					if (SynFun.getImmediateHead(ven) == "be") {
						Annotation vp3 = childWithCat(vp2, "vp");
						if (vp3 != null) {
							Annotation ving = childWithCat(vp3, "ving");
							// perfect progressive
							if (ving != null) {
								FeatureSet pa =  paCopy(ving);
								s.put("pa", pa);
								s.put("mainV", ving);
								recordVg (doc, "vg", vp1.start(), ving.end());
								return vp3;
							}
							Annotation ven2 = childWithCat(vp3, "ven");
							// passive perfect
							if (ven2 != null) {
								FeatureSet pa =  paCopy(ven2);
								s.put("pa", pa);
								s.put("mainV", ven2);
								String idStr = (String) s.get("id"); // For debugging
								if (pa != null)
									pa.put("voice", "passive");
								recordVg (doc, "vg-pass", vp1.start(), ven2.end());
								return vp3;
							}
						}
					}
					// simple perfect
					FeatureSet pa =  paCopy(ven);
					s.put("pa", pa);
					s.put("mainV", ven);
					recordVg (doc, "vg", vp1.start(), ven.end());
					return vp2;
				}
			}
		}
		// Modal ... (including 'did'?)
		Annotation aux = childWithCat(vp1, "w");
		if (aux != null) {
			Annotation vp2 = childWithCat(vp1, "vp");
			if (vp2 != null) {
				Annotation v = childWithCat(vp2, "v");
				if (v!= null) {
					FeatureSet pa =  paCopy(v);
					s.put("pa", pa);
					s.put("mainV", v);
					recordVg (doc, "vg", vp1.start(), v.end());
					return vp2;
				}
			}
		}
		// Ordinary tensed verbs ...
		if (mainV != null) {
			FeatureSet pa =  paCopy(mainV);
			s.put("pa", pa);
			s.put("mainV", mainV);
			recordVg (doc, "vg", vp1.start(), mainV.end());
			return vp1;
		}
		// no main verb found ...
		return null;
	}

	private static void recordVg (Document doc, String cat, int start, int end) {
		doc.annotate("constit", new Span(start, end), new FeatureSet ("cat", cat));
		// System.out.println ("vg = " + doc.text(new Span(start,end)));
	}

	/**
	 *  analyzes the 'vp2' complement of 'be', occurring within a sentence
	 *  subtree 's'.  Recognizes both progressive and passive forms.  Assigns
	 *  to 's' the attribute 'pa' with a 'head' feature specifying the main verb
	 *  and a 'voice' feature with value 'passive' for passive forms.  Returns
	 *  the 'vp' containing the main verb.
	 */

	private static Annotation processBeComplement (Annotation s, Annotation vp2,
	                                               Document doc, Annotation vp1) {
		Annotation ving = childWithCat(vp2, "ving");
		if (ving != null) {
			FeatureSet pa =  paCopy(ving);
			String id = (String) s.get("id"); // For debugging
			s.put("pa", pa);
			s.put("mainV", ving);
			if (vp1 == null) vp1 = ving;
			recordVg (doc, "vg", vp1.start(), ving.end());
			return vp2;
		}
		Annotation ven = childWithCat(vp2, "ven");
		if (ven != null) {
			FeatureSet pa =  paCopy(ven);
			String id = (String) s.get("id"); // For debugging
			if (pa != null)
				pa.put("voice", "passive");
			s.put("pa", pa);
			s.put("mainV", ven);
			if (vp1 == null) vp1 = ven;
			recordVg (doc, "vg-pass", vp1.start(), ven.end());
			return vp2;
		}
		return null;
	}

	/**
	 *  returns the first child of parse tree node 'node' with syntactic
	 *  category 'cat', or null if no such child exists.
	 */

	private static Annotation childWithCat (Annotation node, String cat) {
		Annotation[] children = ParseTreeNode.children(node);
		if (children == null) return null;
		for (int i=0; i<children.length; i++) {
			Annotation child = children[i];
			if (child != null && child.get("cat") == cat) return child;
		}
		return null;
	}

	/**
	 *  returns the first child of parse tree 'node' which is an NP
	 *  whose head is not a time word (attribute ntime).
	 */

	private static Annotation nonTimeNP (Annotation node) {
		Annotation[] children = ParseTreeNode.children(node);
		if (children == null) return null;
		for (int i=0; i<children.length; i++) {
			Annotation child = children[i];
			if (child == null || child.get("cat") != "np") continue;
			Annotation headC = Resolve.getHeadC(child);
			if (headC != null &&
			    ((headC.get("ntime") != null) || headC.get("cat") == "timex")) continue;
			return child;
		}
		return null;
	}

	private static FeatureSet paCopy (Annotation node) {
		Object pa = node.get("pa");
		if (pa == null) return null;
		if (!(pa instanceof FeatureSet)) return null;
		FeatureSet fs = (FeatureSet) pa;
		return new FeatureSet(fs);
	}

	public static final String[] prepositions =
		{"of", "on", "in", "to", "by", "at", "through", "for", "with"};

	/*
	 *  addOfRelation
	 *
	 *  looks for  x= (NP  (NP ...)
	 *                     (PP (IN of)
	 *                         y=(NP ...))
	 *                     ... )
	 *  and adds "of" link on x pointing to y
	 */

	private static void addOfRelation (Document doc, Annotation constit) {
		String cat = (String) constit.get("cat");
		Annotation[] children = ParseTreeNode.children(constit);
		if (cat == "np" && children != null && children.length >= 2) {
			Annotation npHead = children[0];
			Annotation pp = children[1];
			if (npHead != null && npHead.get("cat") == "np" &&
			    pp != null && pp.get("cat") == "pp") {
				Annotation[] ppChildren = (Annotation[]) pp.get("children");
				if (ppChildren.length == 2) {
					Annotation in = ppChildren[0];
					Annotation npObj = ppChildren[1];
					if (in != null && in.get("cat") == "p" &&
					    npObj != null && npObj.get("cat") == "np") {
						String prep = doc.text(in).trim().toLowerCase();
						// if (prep.equals("of")) {
						if (Resolve.in(prep, prepositions)) {
							// System.out.println ("AddSyntacticRelations:  found " + prep + " phrase.");
							constit.put(prep, npObj);
						}
					}
				}
			}
		}
	}

	/**
	 *  adds a feature 'p-obj' to PP nodes pointing to the object of the preposition.
	 */

	private static void addPObjRelation (Document doc, Annotation pp) {
		String cat = (String) pp.get("cat");
		Annotation[] ppChildren = ParseTreeNode.children(pp);
		if (cat == "pp" && ppChildren != null && ppChildren.length >= 2) {
		// Changed for coref context experiment, 10/29/04 MDW -- also added findPrepositionIndex() 11/04/04
		//		if (cat == "pp" && ppChildren != null && ppChildren.length == 2) {
			Annotation in = null, npObj = null;
			int w = findPrepositionIndex(pp);
			if (w >=0 && w < ppChildren.length - 1) {
				in = ppChildren[w];
				npObj = ppChildren[w+1];
			}
			if (in != null && npObj.get("cat") == "np") {
				String prep = doc.text(in).trim();
				pp.put("p-obj", npObj);
				npObj.put("p-obj-1", pp);
			}
		}
	}

	/**
	 *  starting from a PP constituent node, returns the index of the
	 *  child node of category P or DP, or -1 if no such category is found.
	 */

	public static int findPrepositionIndex (Annotation pp) {
		Annotation[] ppChildren = ParseTreeNode.children(pp);
		for (int w=0; w < ppChildren.length; w++) {
			Annotation temp = ppChildren[w];
			if (temp.get("cat").equals("p") || temp.get("cat").equals("dp")) {
				return w;
			}
		}
		return -1;
	}

	/**
	 *  starting from a PP constituent node, returns the preposition,
	 *  (or DP), or null if no preposition can be found.
	 */

	private static String getPreposition (Document doc, Annotation pp) {
		int w = findPrepositionIndex(pp);
		if (w >=0) {
			Annotation in = ParseTreeNode.children(pp)[w];
			return doc.text(in).trim();
		} else {
			return null;
		}
	}

	/**
	 *  starting from a PP constituent node, returns the prepositional
	 *  object (an NP), or null if no such NP can be found.
	 */

	private static Annotation getPPObj (Document doc, Annotation pp) {
		Annotation[] ppChildren = ParseTreeNode.children(pp);
		int w = findPrepositionIndex(pp);
		if (w >=0 && w < ppChildren.length - 1) {
			Annotation npObj = ppChildren[w+1];
			if (npObj.get("cat") == "np")
				return npObj;
		}
		return null;
	}

	public static final String[] comlexDeterminers =
     {"such", "several", "most", "more", "many", "less", "few",
      "enough", "both", "all", "those", "this", "these", "the", "that",
      "some", "no", "neither", "every", "either", "each", "any",
      "another", "an", "a"};
  public static final String[] possessivePronouns =
     {"my", "your", "his", "her", "its", "our", "their"};

	/**
	 *  addDetRelation
	 *
	 *  looks for
	 *    x=(NP (DET y) ... )
	 *  and adds a feature 'det' to the NP, with value 'y' (the value is a String,
	 *  not the node).  All words considered determiners by Comlex are included
	 *  (this is a larger set than those considered determiners by the Penn TreeBank).
	 *  <p>
	 *  If the determiner is a possessive pronoun or a possessive phrase (with 's),
	 *  the 'det' feature is set to "poss", and the 'poss' feature points to the
	 *  possessive pronoun or np.
	 *  <p>
	 *  If the first word of the noun group is a quantifier, the 'det' feature is
	 *  set to "q".
	 *
	 **/

	private static void addDetRelation (Document doc, Annotation constit) {
		String cat = (String) constit.get("cat");
		if (cat != "np") return;
		Annotation ngHead = Resolve.getNgHead(constit);
		Annotation[] children = ParseTreeNode.children(ngHead);
		if (children != null && children.length >= 2) {
			Annotation firstChild = children[0];
			restructurePoss (doc, firstChild);
			String det = doc.text(firstChild).trim().toLowerCase().intern();
			if (Resolve.in(det,comlexDeterminers)) {
				// System.out.println ("AddSyntacticRelations:  found determiner " + det);
				constit.put("det", det);}
			else if (Resolve.in(det,possessivePronouns)) {
				constit.put("det", "poss");
				constit.put("poss", firstChild);
			} else if (firstChild.get("cat") == "np$") {
				constit.put("det", "poss");
				constit.put("poss", ParseTreeNode.children(firstChild)[0]);
			} else if (firstChild.get("cat") == "q" || firstChild.get("cat") == "qp") {
				constit.put("det", "q");
			}
		}
	}

	/**
	 *  addAppositeRelation identifies structures of the form
	 *     (np (np1 ..)
	 *         (, ,)
	 *         (np2 ..)
	 *         (, ,)   << optional
	 *         )
	 *     where exactly one of np1, np2 has a name head, and sets an
	 *     'apposite' link from the top np to np2.
	 */

	private static void addAppositeRelation (Document doc, Annotation constit) {
		String cat = (String) constit.get("cat");
		Annotation[] children = ParseTreeNode.children(constit);
		if (cat == "np" && children != null &&
		    (children.length == 3 || children.length == 4) &&
		    (children[0] != null && children[1] != null && children[2] != null) &&
		    children[0].get("cat") == "np" &&
		    children[1].get("cat") == "," &&
		    children[2].get("cat") == "np" &&
		    (children.length == 3 || (children[3] != null && children[3].get("cat") == ","))) {
		  Annotation head1 = Resolve.getHeadC(children[0]);
		  Annotation head2 = Resolve.getHeadC(children[2]);
		  boolean name1 = head1.get("cat") == "name";
		  boolean name2 = head2.get("cat") == "name";
		  if ((name1 && !name2) || (name2 && !name1)) {
				constit.put("apposite", children[2]);
			  // System.out.println ("AddSyntacticRelations:  found apposition " + doc.text(constit));
			}
		}
	}

	/**
	 *  makes two annotations related to names:  if a name is the head of a
	 *  phrase and is immediately preceded by an N or TITLE ("President Bush"),
	 *  set a 'preName' feature on the NP pointing to the N or TITLE;
	 *  also, for any head with a modifier which is a name ("New York
	 *  highways"), set a 'nameMod' feature on the NP pointing to the modifier.
	 */

	private static void addPreNameRelation (Document doc, Annotation constit) {
		String cat = (String) constit.get("cat");
		if (cat != "np") return;
		Annotation headC = Resolve.getHeadC(constit);
		Annotation ngHead = Resolve.getNgHead(constit);
		Annotation[] children = ParseTreeNode.children(ngHead);
		if (children != null && children.length >= 2) {
			if (headC.get("cat") == "name") {
				Annotation lastLeftMod = children[children.length-2];
				if (lastLeftMod.get("cat") == "title" ||
				    lastLeftMod.get("cat") == "n") {
					constit.put("preName", lastLeftMod);
					lastLeftMod.put("preName-1", constit);
					// System.out.println ("AddSyntacticRelations:  found preName " + doc.text(lastLeftMod) +
					//                     " in " + doc.text(constit));
				}
			}
			for (int i=0; i<children.length-1; i++) {
				Annotation leftMod = children[i];
				if (leftMod.get("cat") == "name") {
					constit.put("nameMod", leftMod);
					leftMod.put("nameMod-1", constit);
				}
			}
		}
	}

	static final String[] predCompVerbs = {"be", "become"};
	//Added MDW 04/04/05:
	static final String[] complementHeadCats = {"adj","ven","pp"};

	/**
	 *  looks for constructs of the form <br>
	 *  NP be/become X <br>
	 *  where X is an NP, ADJ, VEN, or PP
	 *  and adds a 'predComp' feature on the first NP, pointing to X
	 */

	private static void addPredCompRelation (Document doc, Annotation constit) {
		String cat = (String) constit.get("cat");
		Annotation[] children = ParseTreeNode.children(constit);
		if (cat == "s" && children != null && children.length >= 2 &&
		    children[0] != null && children[0].get("cat") == "np" &&
		    children[1] != null && children[1].get("cat") == "vp") {
			Annotation subject = children[0];
			String subjectHead = SynFun.getHead(doc, subject);
			if ("there".equalsIgnoreCase(subjectHead)) return;
			if ("it".equalsIgnoreCase(subjectHead)) return;
			Annotation vp = children[1];
			Annotation[] vpChildren = (Annotation[]) vp.get("children");
			if (vpChildren != null && vpChildren.length >= 2 &&
			    vpChildren[0].get("cat") == "tv" &&
			    Resolve.in(SynFun.getHead(doc, vpChildren[0]), predCompVerbs) &&
				//Changed MDW 04/04/05 (Used to just allow np complements):
			    ((vpChildren[1].get("cat").equals("np"))
			    || Resolve.in(Resolve.getHeadC(vpChildren[1]).get("cat"),complementHeadCats))
			    ) {
				subject.put("predComp", vpChildren[1]);
				// System.out.println ("AddSyntacticRelations:  found copula " + doc.text(constit));
			}
		}
	}

	/**
	 *  looks for conjoined NPs of the form (np (np 1) (cconj ) (np 2)) and adds a
	 *  'conj' feature to np1 pointing to np2.
	 */

	private static void addConjRelation (Document doc, Annotation constit) {
		if (constit.get("cat") != "np") return;
		Annotation[] children = ParseTreeNode.children(constit);
		if (children.length == 3 &&
		    children[0].get("cat") == "np" &&
		    children[1].get("cat") == "cconj" &&
		    children[2].get("cat") == "np") {
			children[0].put("conj", children[2]);
		}
	}

}
