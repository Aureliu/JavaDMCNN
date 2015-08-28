// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Console;

/**
 *  class Retagger provides methods for converting Penn part-of-speech
 *  tags into Jet part-of-speech tags, and using the result to filter
 *  the entries in the Jet lexicon.
 */

public class Retagger {

	private static HashMap map = new HashMap();

	static {
		map.put("CC",  new FeatureSet ("cat", "cconj"));
		map.put("CD",  new FeatureSet ("cat", "q"));
		map.put("DT",  new FeatureSet ("cat", "det"));
		// no Jet class for 'ex'
		// no Jet class for 'fw'
		// 'in' is both p and sconj, handled separately by ptbToJetFS
		map.put("JJ",  new FeatureSet ("cat", "adj"));
		map.put("JJR", new FeatureSet ("cat", "adj"));	// no comparative feature yet
		map.put("JJS", new FeatureSet ("cat", "adj"));   // no superlative feature yet
		// no Jet class for 'ls'
		map.put("MD",  new FeatureSet ("cat", "w"));
		map.put("NN",  new FeatureSet ("cat", "n", "number", "singular"));
		map.put("NNS", new FeatureSet ("cat", "n", "number", "plural"));
		// no Jet class for 'nnp'
		// no Jet class for 'nnps'
		map.put("PDT", new FeatureSet ("cat", "adv"));
		// no Jet class for 'pos' --  recognized by lex scanner
		map.put("PRP", new FeatureSet ("cat", "pro"));
		map.put("PRP$",new FeatureSet ("cat", "det"));
		map.put("RB",  new FeatureSet ("cat", "adv"));
		map.put("RBR", new FeatureSet ("cat", "adv"));	// no comparative feature on adverbs
		map.put("RBS", new FeatureSet ("cat", "adv"));	// no superlative feature on adverbs
		map.put("RP",  new FeatureSet ("cat", "dp"));
		// no Jet class for 'sym'
		map.put("TO",  new FeatureSet ("cat", "p"));	// infinitive marker is literal
		// no Jet class for 'uh'
		// 'do' is tagged VB but handled separately by mapFG
		map.put("VB",  new FeatureSet ("cat", "v"));
		map.put("VBD", new FeatureSet ("cat", "tv"));
		map.put("VBG", new FeatureSet ("cat", "ving"));
		map.put("VBN", new FeatureSet ("cat", "ven"));
		map.put("VBP", new FeatureSet ("cat", "tv", "number", "plural"));
		map.put("VBZ", new FeatureSet ("cat", "tv", "number", "singular"));
		// no Jet class for 'wdt'
		// no Jet class for 'wp'
		// no Jet class for 'wp$'
		// no Jet class for 'wrb'
	}

	/**
	 *  given an annotation based on Penn tag set, returns an array
	 *  (possibly empty) of corresponding Jet FeatureSets, with one entry for
	 *  each possible Jet category and attributes.
	 */

	public static FeatureSet[] ptbToJetFS (String word, String pennPOS) {
		String w = word.toLowerCase().intern();
		if (w == "do" || w == "does" || w == "did" || w == "done") {
			return new FeatureSet[] {new FeatureSet ("cat", "w")};
		} else if (pennPOS == "IN") {
			return new FeatureSet[] {new FeatureSet ("cat", "p"),
									 new FeatureSet ("cat", "sconj")};
		// added for Charniak parser:  AUX and AUXG
		} else if (pennPOS == "AUX") {
			if (w == "be")
				return new FeatureSet[] {new FeatureSet ("cat", "v")};
			else if (w == "been")
				return new FeatureSet[] {new FeatureSet ("cat", "ven")};
			else if (w == "have")
				return new FeatureSet[] {new FeatureSet ("cat", "tv"),
									               new FeatureSet ("cat", "v")};
			else if (w == "had")
				return new FeatureSet[] {new FeatureSet ("cat", "tv"),
									               new FeatureSet ("cat", "ven")};
			else /* am, is, are, was, were, has */
				return new FeatureSet[] {new FeatureSet ("cat", "tv")};
		} else if (pennPOS == "AUXG") {
				return new FeatureSet[] {new FeatureSet ("cat", "ving")};
		} else {
			FeatureSet newFS = (FeatureSet) map.get(pennPOS);
			if (newFS != null )
				return new FeatureSet[] {newFS};
			else
				return new FeatureSet[] {};
		}
	}

	/**
	 *  given a FeatureSet fs for a Jet lexical constituent (with a 'cat'
	 *  feature and possibly other features), return a Penn POS consistent
	 *  with 'fs'.  If several Penn POS's are consistent, one is
	 *  arbitrarily returned.  If none are consistent, 'null' is returned.
	 */

	public static String jetToPtbPos (FeatureSet fs) {
		Iterator it = map.keySet().iterator();
		// loop over ptb pos
		while (it.hasNext()) {
			// get mapping to jet
			String pennPOS = (String) it.next();
			// is it contained in fs?
			FeatureSet jetFS = (FeatureSet) map.get(pennPOS);
			if (jetFS.subsetOf(fs)) {
				// yes, return pos
				return pennPOS;
			}
		}
		// none ... return null
		return null;
	}

	/**
	 *  changes <B>tagger</B> annotations using Penn tags to <B>constit</B>
	 *  annotations using Jet tags
	 */

	static void mapConstit (Document d, Span span) {
		Vector v = d.annotationsOfType("tagger", span);
		if (v == null) return;
		for (int i=0; i<v.size(); i++) {
			Annotation a = (Annotation) v.get(i);
			String textSpanned = d.text(a).trim();
			String cat = (String) a.get("cat");
			FeatureSet[] w = ptbToJetFS(textSpanned, cat);
			for (int j=0; j<w.length; j++) {
				d.annotate("constit", a.span(), new FeatureSet(w[j]));
				if (HMMTagger.trace)
					Console.println ("Annotating " + d.text(a.span()) +
					" as <constit" + w[j].toSGMLString() + ">");
			}
		}
	}

	/**
	 *  prunes <B>constit</B> annotations obtained from lexical look-up
	 *  using Penn tags (recorded as <B>tagger</B> annotations). <p>
	 *  The following rules are applied at each token: <br>
	 *  1. If there are no lexical entries (constit tags), generate
	 *     constit annotations based on tagger tags. <br>
	 *  2. If there is a lexical entry spanning more than one token,
	 *     keep it. <br>
	 *  3. If there are lexical entries, and some of them are
	 *     consistent with the tagger tags, keep only the consistent
	 *     entries.
	 *  4. If the tagger tag is POS (possessive), delete all lexical
	 *     entries.
	 *  5. Otherwise (there are lexical entries, but none are consistent
	 *     with the tagger tags) keep all lexical entries.
	 */

	public static void pruneConstit (Document d, Span zone) {
		Vector pennAnns;
		ArrayList bestJetAnns = new ArrayList();
		int posn = zone.start();
		int end = zone.end();
		// advance to first 'tagger' annotation
		while (d.annotationsAt(posn, "tagger") == null) {
	    	posn++;
	    	if (posn >= end) return;
	    }
		while (posn < end && (pennAnns = d.annotationsAt(posn, "tagger")) != null) {
			Annotation pa = (Annotation) pennAnns.get(0);
			boolean possessiveTag = pa.get("cat").equals("POS");
			Span span = pa.span();
			String token = d.text(span).trim();
			String cat = (String) pa.get("cat");
			FeatureSet[] FSpenn = ptbToJetFS(token, cat);
			Vector jetAnns = d.annotationsAt(posn, "constit");
			// no Jet definitions ... translate Penn tags into Jet classes
			if (jetAnns == null) {
				for (int i=0; i<FSpenn.length; i++) {
					FeatureSet jetFS = new FeatureSet(FSpenn[i]);
					String jetCat = (String) jetFS.get("cat");
					if (jetCat == "n")
						jetFS.put("pa",new FeatureSet("head", token));
					d.annotate("constit", span, jetFS);
					// Console.println ("Adding " + FSpenn[i] + " on " + span);
				}
			} else {
				// token is defined in Jet lexicon;
				// if Jet definition spans multiple tokens (more than the
				// word tagged by the POS tagger), keep the definition(s)
				Annotation firstJetAnn = (Annotation) jetAnns.get(0);
				if (firstJetAnn.end() > span.end()) {
					posn = firstJetAnn.end();
					continue;
				}
				// definition in Jet lexicon only spans a single token
				// see which definitions are consistent with Penn tag
				bestJetAnns.clear();
				for (int i=0; i<jetAnns.size(); i++) {
					Annotation jetAnn = (Annotation) jetAnns.get(i);
					FeatureSet fsJet = jetAnn.attributes();
					for (int j=0; j<FSpenn.length; j++) {
						if (FeatureSet.unify (FSpenn[j], fsJet) != null) {
							bestJetAnns.add(jetAnn);
						}
					}
				}
				// if some definitions from Jet lexicon are consistent with
				// Penn tag, remove other definitions.  If Penn assigned
				// possessive tag (POS), remove all Jet definitions.
				if (!bestJetAnns.isEmpty() || possessiveTag) {
					for (int i=0; i<jetAnns.size(); i++) {
						Annotation a = (Annotation) jetAnns.get(i);
						if (!bestJetAnns.contains(a)) {
							d.removeAnnotation(a);
							if (HMMTagger.trace)
								Console.println ("Removing " + a + " on " + d.text(a.span()));
						}
					}
				}
			}
			posn = span.end();
		}
	}

	/**
	 *  returns true if Penn part-of-speech tag 'pennPOS', as a tag for 'word', is
	 *  compatible with Jet word definition 'jetDefn'.
	 */

	public static boolean compatible (String word, String pennPOS, Annotation jetDefn) {
		FeatureSet[] FSpenn = ptbToJetFS(word.toLowerCase(), pennPOS);
		FeatureSet fsJet = jetDefn.attributes();
		for (int j=0; j<FSpenn.length; j++) {
			if (FeatureSet.unify (FSpenn[j], fsJet) != null) {
				return true;
			}
		}
		return false;
	}

}
