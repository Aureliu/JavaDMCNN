// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2001, 2003, 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Refres;

import java.util.*;
import java.io.*;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Concepts.*;
import Jet.Zoner.SentenceSet;
import Jet.Parser.SynFun;
import Jet.Parser.ParseTreeNode;
import Jet.JetTest;
import Jet.Console;
import Jet.Lex.Tokenizer;
import Jet.Lex.Lexicon;
import AceJet.*;

/**
 *  contains static procedures for reference resolution within a document.
 */

public class Resolve {

	/**
	 *  all clauses in the sentence currently being resolved
	 */
	static Vector<Annotation> clauses;
	/**
	 *  all entities in the document.  Updated as new entities are created.
	 */
	static Vector<Annotation> entities;
	/**
	 *  the set of sentences in the document
	 */
	public static SentenceSet sentenceSet;
	/**
	 *  true if there is a full parse for the sentence.  Determined by
	 *  seeing if there is a <b>sentence</b> annotation with a <b>parse</b>
	 *  attribute.
	 */
	static boolean fullParse = false;
	/**
	 *  set to true if apposites and predicate complements should be
	 *  linked coreferentially.
	 */
	public static boolean linkAppositesAndPredComps = true;
	/**
	 *  set to true if a pair of names can be linked only if they have been
	 *  assigned the same named entity type.
	 */
	public static boolean nameTypeMatch = false;
	/**
	 *  a map from a mention to the entity to which it is assigned by
	 *  reference resolution.
	 */
	static HashMap<Annotation, Annotation> mentionToEntity;
	/**
	 *  syntacticAntecedent[X] = Y if, based on syntactic structure, the
	 *  antecedent of X must be Y.
	 */
	static HashMap<Annotation, Annotation> syntacticAntecedent;
	/**
	 *  true to produce trace messages for the reference resolution process
	 */
	public static boolean trace = false;
	/**
	 *  if there is a full parse of the sentence, its root node
	 */
	static Annotation parseTree = null;
	/**
	 *  if there is a full parse tree, a map from each child node to its parent
	 */
	static HashMap<Annotation, Annotation> parents = null;
	/**
	 *  pronouns which are not expected by 'matchPronoun'
	 */
	static HashSet<String> pronounsNotHandled;
	/**
	 *  true to use the Max Ent resolver
	 */
	public static boolean useMaxEnt = false;
	/**
	 *  <CODE>Resolve.references</CODE> resolves the mentions (noun groups) in
	 *  <CODE>span</CODE> of Document <CODE>doc</CODE>.  It generates
	 *  <B>entity</B> annotations, corresponding to one or more mentions in the
	 *  document which are coreferential.  In addition, for every <B>event</B>
	 *  annotation in <CODE>span</CODE>, it generates an <B>r-event</B>
	 *  annotation in which each feature pointing to a mention is replaced by
	 *  the entity to which that mention has been resolved.
	 */

	public static void references (Document doc, Span span) {
		if (useMaxEnt) {
			MaxEntResolve.references(doc, span);
			return;
		}
		// detect full parse
		int start = span.start();
		Vector sentAnns = doc.annotationsAt(start, "sentence");
		if (sentAnns != null && sentAnns.size() > 0) {
			Annotation sentAnn = (Annotation) sentAnns.get(0);
			fullParse = sentAnn.get("parse") != null;
			parseTree = (Annotation) sentAnn.get("parse");
			parents = SynFun.collectParents(parseTree);
		} else {
			fullParse = false;
		}
		Vector<Annotation> mentions = gatherMentions (doc, span);
		Vector<Annotation> clauses = gatherClauses (doc, span);
		references (doc, span, mentions, clauses);
	}

	public static void references (Document doc, Span span, 
	                               Vector<Annotation> mentions, Vector<Annotation> clauses) {

	// assumes features for constit/ngroup:  PA:[head  det  number human];  isName[binary]

	// assumes features for entity:  head;  name;  number;  human;  position;  mentions

		entities = doc.annotationsOfType("entity");
		if (entities == null) entities = new Vector<Annotation>();
		mentionToEntity = new HashMap<Annotation, Annotation>();

		if (trace) Console.println ("Resolving references");
		sentenceSet = new SentenceSet(doc);
		markMentions (mentions);
		syntacticAntecedent = gatherSyntacticCoref (doc, mentions, clauses);
		pronounsNotHandled = new HashSet<String>();
		for(int i = 0; i < mentions.size(); i++) {
			Annotation mention = (Annotation) mentions.get(i);
			resolveMention (doc, mention);
		}
		updateEvents (doc, span, mentionToEntity);
	}

	/**
	 *  determines range of mentions which participate in reference resolution.
	 *  If false, only noun phrases and possessive pronouns are resolved;  if
	 *  true (for ACE evaluations), a wider range of mentions are included.
	 */

	public static boolean ACE = false;

	/**
	 *  returns the set of all mentions -- constituents which are
	 *  subject to reference resolution.  This includes all noun phrases,
	 *  possessive pronouns, and (for ACE) names.  To avoid duplication, we
	 *  exclude NPs which are the head constituent of other NPs (so that,
	 *  for example, if we have "the man in the chair" as a mention, we
	 *  exclude "the man" as a separate mention.
	 */

	public static Vector<Annotation> gatherMentions (Document doc, Span span) {
		Vector<Annotation> mentions = new Vector<Annotation>();
		Vector<Annotation> heads = new Vector<Annotation>();
		Set nameTokens = null;
		nameTokens = gatherNameTokens(doc, span);
		for(int i = span.start(); i < span.end(); i++) {
			Vector constits = doc.annotationsAt(i,"constit");
			if (constits != null) {
				for (int j = 0; j < constits.size();  j++) {
					Annotation ann = (Annotation) constits.elementAt(j);
					String cat = (String) ann.get("cat");
					// parse tree error
					if (cat == null)
						continue;
					if (cat.equals("ngroup") ||
					    (cat.equals("np") && !conjoinedNP(doc, ann)) ||
					    (cat.equals("det") && ann.get("tposs") == "t") ||
					    (ACE && cat.equals("name")) ||
					    // ACE 2004 ... allow titles
					    (ACE && AceDocument.ace2004 && cat.equals("title") /* && ann.get("hidden")== null */) ||
					    // ACE 2004 ... allow pre-nominal bare nouns
					    (ACE && AceDocument.ace2004 &&
					     (cat.equals("n") || cat.equals("nnp") || cat.equals("nnps")) &&
					      !nameTokens.contains(i)) ||
					    (((ACE && fullParse) || Ace.perfectMentions) && 
					      (cat.equals("whnp") || cat.equals("np-pro"))) ||
					    (Ace.perfectMentions && 
					     (cat.equals("adj") || cat.equals("adv")) && !nameTokens.contains(i))) {
					  Annotation headC = getHeadC(ann);
					  if (headC.get("cat") == "timex") continue;
					  if (headC.get("cat") == "ordinal") continue;
					  if (!Ace.perfectMentions) {
					  	if (headC.get("cat") == "adv") continue;
					  	String head = SynFun.getHead(doc, ann);
					  	if ("there".equalsIgnoreCase(head)) continue;
					  }
					  // excluding 'ving' is risky ... not sure what PTB calls a ving
					  if (headC.get("cat") == "ving") continue;
					  if (Ace.perfectMentions && !PerfectAce.validMention(doc, headC, cat))
					  	continue;
						mentions.add(ann);
						Annotation immHeadC = (Annotation) ann.get("headC");
						if (immHeadC != null) {
							heads.add(immHeadC);
						}
					}
				}
			}
		}
		mentions.removeAll(heads);
		return mentions;
	}
	
	/**
	 *  returns a set of the starting positions of all tokens which are part of
	 *  a name.  This is used to block such tokens from being treated as
	 *  separate mentions by gatherMentions, in addition to being part of a
	 *  name mention.
	 */
	
	private static Set<Integer> gatherNameTokens (Document doc, Span span) {
		Set<Integer> nameTokens = new HashSet<Integer>();
		for(int i = span.start(); i < span.end(); i++) {
			Vector constits = doc.annotationsAt(i,"constit");
			if (constits != null) {
				for (int j = 0; j < constits.size();  j++) {
					Annotation ann = (Annotation) constits.elementAt(j);
					String cat = (String) ann.get("cat");
					if (cat == "name") {
						int posn = ann.start();
						while (posn < ann.end()) {
							nameTokens.add(posn);
							Annotation tok = doc.tokenAt(posn);
  	      		if (tok == null) break;
    	    		posn = tok.span().end();
    	    	}
    	    }
    	  }
    	}
    }
    return nameTokens;
  }

	/**
	 *  returns the set of all clauses (constituents of category s, rn-wh,
	 *  or rn-vingo) within Span <CODE>span</CODE> of Document <CODE>doc</CODE>.
	 */

	public static Vector<Annotation> gatherClauses (Document doc, Span span) {
		clauses = new Vector<Annotation>();
		for(int i = span.start(); i < span.end(); i++) {
			Vector constits = doc.annotationsAt(i,"constit");
			if (constits != null) {
				for (int j = 0; j < constits.size();  j++) {
					Annotation ann = (Annotation) constits.elementAt(j);
					String cat = (String) ann.get("cat");
					// parse tree error
					if (cat == null)
						continue;
					if (cat.equals("s") || cat.equals("rn-wh") || cat.equals("rn-vingo")) {
						clauses.add(ann);
					}
				}
			}
		}
		return clauses;
	}

	private static boolean conjoinedNP (Document doc, Annotation ann) {
		// *** note this depends on parser
		Annotation[] children = ParseTreeNode.children(ann);
		if (children == null || children.length != 3)
			return false;
		if (children[0] == null || children[1] == null || children[2] == null)
			return false;
		boolean answer = children[0].get("cat") == "np" &&
		       children[1].get("cat") == "cconj" &&
	         children[2].get("cat") == "np";
	  return answer;
	}

	/**
	 *  for all constituents on <CODE>mentions</CODE>, set the feature
	 *  <B>mention</B> to <B>true</B>.
	 */

	public static void markMentions (Vector mentions) {
		if (mentions == null) return;
		for (int i=0; i<mentions.size(); i++) {
			Annotation mention = (Annotation) mentions.get(i);
			mention.put("mention", "true");
		}
	}

	/**
	 *  gatherSyntacticCoref looks for particular syntactic patterns in the
	 *  text which indicate coreference, and returns a Map with one entry
	 *  for each such syntactic coreference, linking the anaphor to the
	 *  antecedent.
	 **/

	public static HashMap<Annotation, Annotation> gatherSyntacticCoref (Document doc, 
			Vector<Annotation> mentions, Vector<Annotation> clauses) {
		HashMap<Annotation, Annotation> syntacticAntecedent = 
			new HashMap<Annotation, Annotation>();
		// we need to copy mentions' because its elements are reordered by recordSyntaticCoref
		Vector<Annotation> mentions2 = new Vector<Annotation> (mentions);
		for (Annotation mention : mentions2) {
			Annotation headC = getHeadC(mention);
			String head = SynFun.getHead(doc, mention);
			Annotation ngHead = getNgHead(mention);
			if (head == null) {
				System.err.println ("No head for annotation " + mention +
									" over " + doc.text(mention));
				continue;
			}
			if (mention.get("preName") != null) {
				Annotation preName = (Annotation) mention.get("preName");
				recordSyntacticCoref (preName, mention, doc, mentions, syntacticAntecedent);
			}
			// {city | state | ...} of X  =coreferential with= X
			String number = SynFun.getNumber(mention);
			if (head != null &&
			    (head.equals("city") || head.equals("state") || head.equals("county") ||
			     head.equals("village") || head.equals("town") || head.equals("island") ||
			     head.equals("port") || head.equals("province") || head.equals("district") ||
			     head.equals("region"))
			    && (number == null || !number.equals("plural"))) {
				Annotation of = (Annotation) mention.get("of");
				Annotation nameMod = (Annotation) mention.get("nameMod");
				if (of != null && isName(getHeadC(of))) {
					recordSyntacticCoref(mention, of, doc, mentions, syntacticAntecedent);
					if (trace) System.out.println ("Found X of Y coref pair: " + doc.text(mention));
				} else if (nameMod != null) {
					recordSyntacticCoref(mention, nameMod, doc, mentions, syntacticAntecedent);
					if (trace) System.out.println ("Found nameMod X coref pair: " + doc.text(mention));
				}
			}
			// {all | both} of X = coreferential with= X
			if (head != null && (head.equals("all") || head.equals("both"))) {
				Annotation of = (Annotation) mention.get("of");
				if (of != null) {
					recordSyntacticCoref(of, mention, doc, mentions, syntacticAntecedent);
					if (trace) System.out.println ("Found X of Y coref pair: " + doc.text(mention));
				}
			}
			// apposite of X ('the baker' in 'Fred, the baker') =coreferential with= X
			if (linkAppositesAndPredComps && mention.get("apposite") != null) {
				Annotation apposite = (Annotation) mention.get("apposite");
				if (nameTypeMatch && !typeMatch(doc, mention, apposite)) continue;
				recordSyntacticCoref(apposite, mention, doc, mentions, syntacticAntecedent);
				if (trace) System.out.println ("Refres: found apposition coref pair: " + doc.text(mention));
			}
			// predicate complement of X ('the baker' in 'Fred is a baker') =coreferential with= X
			if (linkAppositesAndPredComps && mention.get("predComp") != null) {
				Annotation predComp = (Annotation) mention.get("predComp");
				// ignore predicate complements which are not mentions, such as PPs and ADJPs
				if (!mentions.contains(predComp)) continue;
				if (nameTypeMatch && !typeMatch(doc, mention, predComp)) continue;
				recordSyntacticCoref(predComp, mention, doc, mentions, syntacticAntecedent);
				if (trace) System.out.println ("Refres: found predComp coref pair: " + doc.text(mention)
				                    + " = " + doc.text(predComp));
			}
			// parenthesized apposite of X ('KO' in 'Coca-Cola (KO)') =coreferential with= X
			if (mention.get("paren") != null) {
				Annotation apposite = (Annotation) mention.get("paren");
				recordSyntacticCoref(apposite, mention, doc, mentions, syntacticAntecedent);
				if (trace) System.out.println ("Refres: found parenthesized apposition coref pair: "
				                               + doc.text(mention));
			}
			// tie whnp ('who', 'which') to host
			if (mention.get("host") != null) {
				Annotation host = (Annotation) mention.get("host");
				recordSyntacticCoref(mention, host, doc, mentions, syntacticAntecedent);
			}
		}
		// when using chunker, we must explicitly extract predicate complement relations;
		// they are not annotated in constituent structure
		for (int i=0; i<clauses.size(); i++) {
			Annotation clause = (Annotation) clauses.get(i);
			String head = SynFun.getHead(doc, clause);
			if (head == null) {
				System.err.println ("No head for annotation " + clause +
									" over " + doc.text(clause));
				continue;
			}
			if (clause.get("subject") != null) {
				Annotation subject = (Annotation) clause.get("subject");
				String subjectHead = SynFun.getHead(doc, subject);
				if ("there".equalsIgnoreCase(subjectHead)) continue;
				if ("it".equalsIgnoreCase(subjectHead)) continue;
				Annotation vp = (Annotation) clause.get("headC");
				if (vp != null && vp.get("object") != null) {
					Annotation object = (Annotation) vp.get("object");
					if (head.equals("be") || head.equals("become")) {
						recordSyntacticCoref(object, subject, doc, mentions, syntacticAntecedent);
						if (trace) System.out.println ("Found copula relation: " + doc.text(clause));
					}
				}
			}

		}
		return syntacticAntecedent;
	}

	/**
	 *  records the coreference relation between <CODE>anaphor</CODE> and
	 *  <CODE>antecedent</CODE> based on their syntactic relationship.
	 *
	 *  @param anaphor     the anaphor (a mention)
	 *  @param antecedent  the antecedent (a mention)
	 *  @param doc         the document containing both mentions
	 *  @param mentions    a Vector of all mentions being resolved;
	 *                     elements will be permuted if necessary so that
	 *                     <CODE>antecedent</CODE> precedes <CODE>anaphor</CODE>
	 *                     and is therefore resolved first
	 *  @param syntacticAntecedents a map from anaphora to antecedents,
	 *                     which will be augmented by the
	 *                     <CODE>anaphor</CODE>,<CODE>antecedent</CODE> pair
	 */

	private static void recordSyntacticCoref (Annotation anaphor, Annotation antecedent,
	                                          Document doc, Vector<Annotation> mentions, 
	                                          Map<Annotation, Annotation> syntacticAntecedent) {
		// antecedent should be first in mentions sequence
	  int antecedentPosn = mentions.indexOf(antecedent);
	  if (antecedentPosn < 0) {
	  	System.err.println ("Antecedent not in mentions: " + doc.text(antecedent));
	  	return;
	  }
	  int anaphorPosn = mentions.indexOf(anaphor);
	  if (anaphorPosn < 0) {
	  	System.err.println ("Anaphor not in mentions: " + doc.text(anaphor));
	  	return;
	  }
	  // make sure antecedent is first on mentions, so it will be resolved first
	  if (antecedentPosn > anaphorPosn) {
	  	mentions.set(antecedentPosn, anaphor);
	  	mentions.set(anaphorPosn, antecedent);
	  }
		// does anaphor already have a recorded antecedent?
		Annotation prior = (Annotation) syntacticAntecedent.get(anaphor);
		if (prior != null) {
			antecedentPosn = mentions.indexOf(antecedent);
			int priorPosn = mentions.indexOf(prior);
			if (antecedentPosn < priorPosn) {
				syntacticAntecedent.put(prior, antecedent);
			} else {
				syntacticAntecedent.put(anaphor, antecedent);
				syntacticAntecedent.put(antecedent, prior);
			}
		} else {
			syntacticAntecedent.put(anaphor, antecedent);
		}
	}

	private static boolean typeMatch (Document doc, Annotation men1, Annotation men2) {
		String type1 = EDTtype.bareType(EDTtype.getTypeSubtype(doc, null, men1));
		String type2 = EDTtype.bareType(EDTtype.getTypeSubtype(doc, null, men2));
		if (type1.equals(type2))
			return true;
		// System.out.println ("Resolve.typeMatch rejected coreference of " +
		//                     doc.text(men1) + " and " + doc.text(men2));
		return false;
	}

	/**
	 *  updates events based on reference resolution.  For each constituent of
	 *  type <B>event</B>, creates a constituent of type <B>r-event</B>,
	 *  replacing each mention argument with the corresponding entity.
	 */

	public static void updateEvents (Document doc, Span span, Map mentionToEntity) {
		for(int i = span.start(); i < span.end(); i++) {
			Vector events = doc.annotationsAt(i,"event");
			if (events != null) {
				Annotation event = (Annotation) events.elementAt(0);
				FeatureSet resolvedFeatures = new FeatureSet(event.attributes());
				for (Enumeration features = resolvedFeatures.keys();
				     features.hasMoreElements(); ) {
					String feature = (String) features.nextElement();
					Object value = resolvedFeatures.get(feature);
					if (mentionToEntity.containsKey(value))
						resolvedFeatures.put(feature,mentionToEntity.get(value));
				}
				doc.annotate("r-event", event.span(), resolvedFeatures);
			}
		}
	}

	/**
	 *  perform anaphora resolution on 'mention' in Document 'doc'.
	 *  either add it to an existing entity, or create a new entity.
	 */

	private static void resolveMention (Document doc, Annotation mention) {
		ArrayList<Annotation> antecedents = null;
		if (fullParse)
		  antecedents = Hobbs.collectAntecedents (mention, parents, doc);
		Annotation headC = getHeadC(mention);
		String cat = (String) headC.get("cat");
		// get properties of anaphor ---------------------------------------------
		int mentionPosition = mention.span().start();
		String mentionHead = SynFun.getHead(doc, mention);
		// Added 10/02/05 by MDW. For multiple hypotheses, we can't
		// get the name type from the pa structure (as SynFun tries to do)
		// -- we have to get it from the enamex tag instead.
		if (mentionHead.equals("?") && cat.equalsIgnoreCase("name")) {
		    Vector names = doc.annotationsAt(mentionPosition, "ENAMEX");
		    // We assume that names aren't nested
		    if (names != null && names.size() >= 1) {
		        Annotation enamex = (Annotation) names.firstElement();
	      	    FeatureSet atts = enamex.attributes();
	      	    mentionHead = (String) atts.get("TYPE");
		    }
		}
		// --- end MDW mod
		if (mentionHead == null)
			return;
		String[] mentionName = getNameTokens (doc, mention);
		boolean isNameMention = mentionName != null;
		boolean properAdjective = false;
		if (isNameMention) {
			boolean notNP = mention.get("cat") != "np";
			if (notNP && Ace.gazetteer.isNationality(mentionName))
				properAdjective = true;
			// should apply test only if adjective
			mentionName = normalizeGazName(mentionName, notNP, trace);
		}
		int bestDistance = 9999;
		int bestDissimilarity = 999;
		Annotation bestEntity = null;
		boolean reflexive = false;
		// look for syntactically-determined antecedent ----------------------------
		if (syntacticAntecedent.containsKey(mention) && !Ace.perfectEntities) {
			if (trace) System.out.println ("Using syntactically-determined antecedent.");
			Annotation antecedent = syntacticAntecedent.get(mention);
			bestEntity = (Annotation) mentionToEntity.get(antecedent);
			if (bestEntity == null) {
				System.err.println ("Resolve:  syntactic antecedent not in entity");
				System.err.println ("          mention:    " + doc.text(mention));
				System.err.println ("          antecedent: " + doc.text(antecedent));
			}
		// else search for antecedent, taking most recently mentioned matching entity --
		} else {
			for (int ie=0; ie<entities.size(); ie++) {
				int dissimilarity = 0;
				Annotation ent = (Annotation) entities.elementAt(ie);
				boolean match = false;
				if (Ace.perfectMentions & !Ace.perfectEntities) {
					String eTypeSubtype = (String) ent.get("typeSubtype");
					String typeSubtype = PerfectAce.getTypeSubtype(headC);
					if (eTypeSubtype != null && typeSubtype != null && !typeSubtype.equals("") &&
					    !typeSubtype.equals(eTypeSubtype)) {
						// System.out.println ("Rejecting merge of " + doc.text(ent) + " with type " + eTypeSubtype);
						// System.out.println ("              with " + doc.text(mention) + " with type " + typeSubtype);
						continue;
					}
				}
				if (Ace.perfectEntities) {
					String eid = PerfectAce.getEntityID(headC);
					match = ent.get("entityID") != null && ent.get("entityID").equals(eid);
				} else
				if (isNameMention) {
					dissimilarity = matchName (mentionName, mentionHead, ent);
					match = dissimilarity >= 0;
				} else if (cat == "pro" || cat == "det" || cat == "np") {
					String pronoun = mentionHead.toLowerCase().intern();
					match = matchPronoun (doc, mention, pronoun, ent);
					reflexive = reflexives.contains(pronoun);
				// adj and ven (headless np's) are treated like n for resolution
				// v and tv heads for np's are assumed to be tagging errors
				// adv is for 'here', 'there', 'abroad' (perfectMentions only)
				} else if (cat == "n" || cat == "adj" || cat == "ven" || cat == "v" ||
				           cat == "tv" || cat == "hyphword" || cat == "title" ||
				           cat == "nnp" || cat == "nnps" || cat == "adv") {
					match = matchNom (doc, mention, ent);
				} else if (cat == "$") {
					match = false;
				} else if (cat == "q") {
					match = false;
				} else {
					System.err.println ("Unexpected head cat " + cat + " for " + doc.text(mention));
					match = false;
					break;
				}
				if (match) {
					boolean sameSimplex = false;
					if (reflexive) {
						Annotation lastMention = (Annotation) ent.get("lastMention");
						sameSimplex = Hobbs.sameSimplex(lastMention , mention, parents);
					}
					int distance = (reflexive&sameSimplex) ?
					               0 :
					               distance (doc, ent, mention, fullParse, antecedents);
					if (dissimilarity < bestDissimilarity ||
					    (dissimilarity == bestDissimilarity && distance < bestDistance)) {
							bestDistance = distance;
							bestDissimilarity = dissimilarity;
							bestEntity = ent;
					}
				}
			}
		}

		// if no suitable antecedent is found, create a new entity
		if (bestEntity == null) {
			bestEntity =
				createNewEntity (doc, mention, mentionHead, properAdjective, entities);
		} else {
			if (bestEntity.get("properAdjective") != null && !properAdjective)
					bestEntity.put("properAdjective", null);
			if (trace) Console.println ("Resolving " + doc.text(mention) + " to " +
			                            doc.text(bestEntity));
		}
		addMentionToEntity (doc, mention, mentionHead, mentionName,
		                    bestEntity, mentionToEntity);
	}

	/**
	 *  creates and returns a new entity with mention <CODE>mention</CODE>.
	 */

	static Annotation createNewEntity (Document doc, Annotation mention,
			String mentionHead, boolean properAdjective, Vector<Annotation> entities) {
		Annotation entity = doc.annotate("entity", mention.span(),
			                               new FeatureSet ("mentions", new Vector()));
		//
		//  determine semantic number
		//
		String mentionNumber = SynFun.getNumber(mention);
		if (mentionNumber == null)
			mentionNumber = "singular";
		//  (some of this can go into Jet dictionary)
		if (mentionHead.equals("they") || mentionHead.equals("them") ||
		    mentionHead.equals("their") ||
		    mentionHead.equals("these") || mentionHead.equals("those") ||
		    mentionHead.equals("some") ||  mentionHead.equals("many") ||
		    mentionHead.equals("everybody") || mentionHead.equals("everyone"))
			mentionNumber = "plural";
		//  mark NPs with an explicit number head as plural
		Annotation headC = getHeadC(mention);
	  if (headC.get("cat") == "q") {
			Annotation token = doc.tokenAt(headC.start());
			if (token != null && token.get("intvalue") != null)
				mentionNumber = "plural";
		}
		//
		//  determine human / non-human
		//
		entity.put("number", mentionNumber);
		boolean isHumanMention;
		if (EDTtype.isDictLoaded()) {
			String ACEtype = EDTtype.getTypeSubtype(doc, null, mention);
			entity.put("ACEtype", ACEtype);
			isHumanMention = EDTtype.bareType(ACEtype).equals("PERSON");
		} else {
			isHumanMention = SynFun.getHuman(mention) || mentionHead == "PERSON";
		}
		String x = nominativeFormOf(mentionHead);
		if (x == "he" || x == "she")
			isHumanMention = true;
		//   following code is 'correct' but harms refres performance
		// if (mentionHead.equals("somebody") || mentionHead.equals("someone") ||
		//     mentionHead.equals("anybody") || mentionHead.equals("anyone"))
		// 	isHumanMention = true;
		if (isHumanMention)
			entity.put("human", "t");
		entities.addElement(entity);
		if (properAdjective)
				entity.put("properAdjective", "true");
		if (Ace.perfectEntities)
			entity.put("entityID", PerfectAce.getEntityID(getHeadC(mention)));
		if (trace) Console.println ("Creating new entity for " + doc.text(mention));
		return entity;
	}

	/**
	 *  add mention <CODE>mention</CODE> to entity <CODE>entity</CODE>.
	 */

	static void addMentionToEntity (Document doc, Annotation mention,
			String mentionHead, String[] mentionName, Annotation entity,
			Map<Annotation, Annotation> mentionToEntity) {
		entity.put("lastMention", mention);
		entity.put("position", new Integer(mention.span().start()));
		Vector<Annotation> mentions = (Vector<Annotation>) entity.get("mentions");
		mentions.addElement(mention);
		mentionToEntity.put(mention, entity);
		boolean isNameMention = mentionName != null;
		// if this is a name mention, and entity does not already have a name
		// (from a prior mention), add name information
		if (isNameMention) {
			if (entity.get("name") == null) {
				entity.put("name", mentionName);
				Annotation ngHead = Resolve.getNgHead(mention);
				String[] mentionTokens = Tokenizer.gatherTokenStrings(doc, ngHead.span());
				entity.put("nameWithMods", mentionTokens);
				entity.put("nameType", mentionHead);
			}
		} else {
			if (entity.get("head") == null) {
				entity.put("head", nominativeFormOf(mentionHead));
			}
		}
		assignGenderFeature (mentionHead, mentionName, entity);
		if (Ace.perfectMentions) {
			if (entity.get("typeSubtype") == null) {
				String typeSubtype = PerfectAce.getTypeSubtype(getHeadC(mention));
				if (typeSubtype != null && !typeSubtype.equals(""))
					entity.put("typeSubtype", typeSubtype);
			}
		}
	}

	private static final String[] maleHeads =
		{"mr", "mr.", "husband", "father", "grandfather", "son", "grandson", "uncle",
		       "brother", "man", "gentleman", "sir", "boy", "boyfriend",
		       "groom", "bridegroom"};
	private static final String[] femaleHeads =
		{"mrs", "mrs.", "ms", "ms.", "wife", "mother", "grandmother", "daughter",
		 "granddaughter", "aunt", "sister", "woman", "lady", "girl", "girlfriend",
		 "bride"};

	private static HashMap<String,String> genderDict = null;

	/**
	 *  if a mention being added to an entity is clearly male or female, add a
	 *  <B>gender</B> attribute to the entity.  The gender can be indicated by
	 *  a particular NP head ('husband'), title head ('mrs'), or, for person
	 *  names, a first name ('mary').  We use a dictionary of first names from
	 *  the 1990 US Census which were at least 90% male or 90% female.
	 */

	static void assignGenderFeature
			(String mentionHead, String[] mentionName, Annotation entity) {
		String head = mentionHead.toLowerCase();
		if (entity.get("gender") != null)
			return;
		if (in(head, maleHeads))
			entity.put("gender", "male");
		else if (in(head, femaleHeads))
			entity.put("gender", "female");
		else if (mentionHead == "PERSON" && mentionName != null && genderDict != null) {
			String firstName = mentionName[0].toLowerCase();
			String gender = genderDict.get(firstName);
			if (gender != null && gender.equals("M"))
				entity.put("gender", "male");
			else if (gender != null && gender.equals("F"))
				entity.put("gender", "female");
		}
	}

	/**
	 *  if property NameGender.fileName is specified in config, reads the
	 *  name gender dictionary from the specified file.  Each line of the
	 *  dictionary should have a name (lower case), one or more blanks, and
	 *  'M' (for male names) or 'F' (for female names).
	 */

	public static void readGenderDict (String dataPath, Properties config) {
		String fileName = config.getProperty("NameGender.fileName");
		if (fileName == null)
			return;
		System.err.println ("Reading gender dictionary " + fileName);
		String line;
		genderDict = new HashMap<String, String>();
		try {
			BufferedReader reader = new BufferedReader
				(new FileReader (dataPath + File.separatorChar + fileName));
			while ((line = reader.readLine()) != null) {
				String[] fields = line.split(" +");
				String name = fields[0];
				String gender = fields[1];
				genderDict.put(name, gender);
				/* -- not helpful for NE
					String[] nameArray = new String[1];
					nameArray[0] = name;
					Lexicon.addEntry (nameArray, new FeatureSet ("type", "givenName"), "onoma");
				*/
			}
			reader.close();
		} catch (IOException e) {
			System.err.println ("Error in readGenderDict:" + e);
		}
	}

	/**
	 *  compute the distance between [the last mention of] entity and
	 *  mention, using either Hobbs distance or a character distance.
	 *
	 *  @param doc         the document
	 *  @param entity      the potential antecedent
	 *  @param mention     the anaphor
	 *  @param parse       true if a full parse is available
	 *  @param antecedents if parse is true, a list of the antecedents of
	 *                     mention in the same sentence, in Hobbs order
	 *                     (as produced by Hobbs.collectAntecedents)
	 */

	static int distance (Document doc, Annotation entity, Annotation mention,
	                     boolean parse, ArrayList<Annotation> antecedents) {
		int mentionPosition = mention.span().start();
		//  code to find minimum distance [tried April 20 2006, didn't help]
		int distance = 999999;
		Vector anteMentions = (Vector) entity.get("mentions");
		for (int i=0; i<anteMentions.size(); i++) {
			Annotation anteMention = (Annotation) anteMentions.get(i);
			int anteMentionPosition = anteMention.span().start();
			int d;
			if (parse)
				d = Hobbs.distance(doc, anteMention, mention, antecedents, sentenceSet.sentences());
			else
			 	d = sentenceSet.pseudoHobbsDistance(anteMentionPosition, mentionPosition);
			distance = Math.min(distance, d);
		}
		/* -- earlier version -- use last mention only
		Annotation lastMention = (Annotation) entity.get("lastMention");
		int lastMentionPosn = ((Integer) entity.get("position")).intValue();
		int distance;
		if (parse)
			distance = Hobbs.distance(lastMention, mention, antecedents, sentences);
		else
			distance = pseudoHobbsDistance(lastMentionPosn, mentionPosition);
		/* System.err.println ("Hobbs dist. from " + doc.text(lastMention) + " to " +
		                     doc.text(mention) + " is " + distance); */
		return distance;
	}

	/**
	 *  returns a standardized country name, using the gazetteer.  If 'name'
	 *  is a variant country name or a country adjective ('French'), returns
	 *  the standard country name.
	 */

	public static String[] normalizeGazName (String[] name, boolean notNP, boolean trace) {
		// if no gazetteer, no change
		if (Ace.gazetteer == null)
			return name;
		// should apply test only if adjective
		if (notNP && Ace.gazetteer.isNationality(name)) {
			String[] old = name;
			name = Ace.gazetteer.nationalityToCountry(name);
			if (trace)
				System.out.println ("Refres: using country " + concat(name) +
			    	                " for nationality " + concat(old));
		}
		if (Ace.gazetteer.isCountryAlias(name)) {
			String[] old = name;
			name = Ace.gazetteer.canonicalCountryName(name);
			if (trace)
				System.out.println ("Refres: using country " + concat(name) +
			    	                " for alias " + concat(old));
		}
		return name;
	}

	/**
	 *  returns true if nominal mention 'anaphor' is a possible antecedent
	 *  of entity.
	 */

	private static final String[] definiteDets =
		{"the", "this", "these", "that", "those"};
	// private static final String[] indefiniteDets =
	// 	{"few", "afew", "more", "many", "most", "some", "any"};
	private static final String[] indefiniteDets =
		{"few", "afew", "more", "many", "most", "some", "any", "several", "less",
		 "neither", "another", "such", "no", "either"};

	static boolean matchNom (Document doc, Annotation anaphor, Annotation entity) {
		int entityPosition = ((Integer)entity.get("position")).intValue();
		int anaphorPosition = anaphor.span().start();
		int numberofPriorMentionsOfEntity = ((Vector)entity.get("mentions")).size();
		// if (numberofPriorMentionsOfEntity == 1 &&
		// 	sentencesBetween(entityPosition, anaphorPosition) > 4) return false;
		String anaphorHead = SynFun.getHead(doc, anaphor);
		if (anaphorHead == null)
			return false;
		String anaphorDet = SynFun.getDet(anaphor);
		String anaphorNumber = SynFun.getNumber(anaphor);
		if (anaphorNumber == null)
			anaphorNumber = "singular";
		Annotation ng = getNgHead(anaphor);
		Concept anaphorConcept = null;
		if (JetTest.conceptHierarchy != null)
			anaphorConcept = JetTest.conceptHierarchy.getConceptFor(anaphorHead);
		String entityNumber = (String) entity.get("number");
		// anaphoricity test:
		//    NP has a definite determiner and no quantifier
		//    2003, 2004:  too restrictive
		// if (anaphorDet == null || !in(anaphorDet, definiteDets)) return;
		if (in(anaphorDet, indefiniteDets))
		 	return false;
		if (ng.get("quant") != null || anaphorDet == "q")
			return false;
		if (ng.get("poss") != null)
			return false;

		// number feature agreement
		if (!(anaphorNumber == null || entityNumber == null || anaphorNumber.equals(entityNumber)))
			return false;

		// if nominal matches name of entity, return true
		if (nameNomCoref(doc, anaphorDet, anaphorHead, anaphor, entity))
			return true;

		String[] anaphorTokens = getNgTokens(doc, anaphor);
		String[] anaphorLeftModifiers = getLeftModifierTokens(doc, anaphor);
		String[] anaphorRightModifiers = getRightModifierTokens(doc, anaphor);
		String[] anaphorModifiers = concat(anaphorLeftModifiers, anaphorRightModifiers);

		Vector mentions = (Vector) entity.get("mentions");
		for (int i=0; i<mentions.size(); i++) {
			Annotation antecedent = (Annotation) mentions.get(i);
			Annotation antecedentHeadC = getHeadC(antecedent);
			String aCat = (String) antecedentHeadC.get("cat");
			// if mention is not a noun, continue
			if (aCat == "pro" || aCat == "det")
				continue;
			String antecedentDet = SynFun.getDet(antecedent);
			String antecedentHead = SynFun.getHead(doc, antecedent);
			if (antecedentHead == null)
				continue;
			Concept antecedentConcept = null;
			if (JetTest.conceptHierarchy != null)
				antecedentConcept = JetTest.conceptHierarchy.getConceptFor(antecedentHead);
			boolean synonym = false;
			/*
				!anaphorHead.equals(antecedentHead)
				&& entity.get("name") == null
			  && WordNetInterface.isNounSynonym(anaphorHead, antecedentHead);
			*/
			boolean headCompatibility =
			         (anaphorConcept != null && antecedentConcept != null &&
			          JetTest.conceptHierarchy.isaStar (antecedentConcept, anaphorConcept))
			         || anaphorHead.equals(antecedentHead)
			         || synonym;
			if (!headCompatibility) continue;
			// Adam condition (5) (B), excluding generic test
			if (antecedentDet == null)
				if (anaphorModifiers.length > 0 || anaphorDet != null)
					return false;
			//
			String[] antecedentTokens = getNgTokens(doc, antecedent);
			String[] antecedentLeftModifiers = getLeftModifierTokens(doc, antecedent);
			String[] antecedentRightModifiers = getRightModifierTokens(doc, antecedent);
			String[] antecedentModifiers = concat(antecedentLeftModifiers, antecedentRightModifiers);
			boolean leftModifierCompatibility =
			                     intersect(anaphorLeftModifiers, antecedentLeftModifiers);
			boolean ofModifierCompatibility =
			                     intersect(anaphorRightModifiers, antecedentRightModifiers);
			boolean modifierCompatibility =
								 intersect(anaphorModifiers, antecedentModifiers);
			if (!(leftModifierCompatibility && ofModifierCompatibility)) {
			// if (!modifierCompatibility) {
				if (trace) {
					System.out.println ("Refres: modifier compability rejects merge of");
					System.out.println ("        " + doc.text(antecedent) + " and " + doc.text(anaphor));
				}
				return false;
			}
			if (equalArray(anaphorTokens, antecedentTokens) &&
			    equalArray(anaphorRightModifiers, antecedentRightModifiers)) return true;
			if (numberofPriorMentionsOfEntity == 1 &&
				sentenceSet.sentencesBetween(entityPosition, anaphorPosition) > 4) return false;
			if (synonym) {
				System.out.println ("******* WordNet allowed merge of ");
				System.out.println ("        " + doc.text(antecedent) + " and " + doc.text(anaphor));
			}
			return true;
		}
		return false;
	}

    private static String[] getNgTokens (Document doc, Annotation mention) {
    	Annotation ngHead = getNgHead(mention);
    	return Tokenizer.gatherTokenStrings (doc, ngHead.span());
    }

	private static String[] getLeftModifierTokens (Document doc,
			Annotation mention) {
		// we collect all tokens in the noun group preceeding the head
		// except for determiners ...
		Annotation ngHead = getNgHead(mention);
		int ngStart = ngHead.start();
		Annotation headC = getHeadC(mention);
		int headCstart = headC.start();
		int posn = ngStart;
		Annotation token;
		ArrayList<String> mods = new ArrayList<String>();
	posnLoop:
		while (posn < headCstart) {
			Vector constits = doc.annotationsAt (posn, "constit");
			if (constits != null) {
				for (int i=0; i<constits.size(); i++) {
					Annotation constit = (Annotation) constits.get(i);
					String cat = (String) constit.get("cat");
					if (cat == "det" || cat == "ving" || cat == "ven") {
						posn = constit.span().end();
						continue posnLoop;
					}
				}
			}
			token = doc.tokenAt(posn);
			// --- fix by MDW
			if (token==null) {
			    posn++;
			    continue;
			}
			String text = doc.text(token);
			if (text !=null){
				text = text.trim();
				mods.add(text);
			}
			posn = token.span().end();
			if (token.span().start() == token.span().end()) posn++;
		}
		return (String[]) mods.toArray(new String[mods.size()]);
	}

	private static String[] getRightModifierTokens (Document doc, Annotation mention) {
		Annotation headC = getHeadC(mention);
		int headEnd = headC.end();
		int extentEnd = mention.end();
		if (headEnd == extentEnd)
			return new String[0];
	  String[] tokens = Tokenizer.gatherTokenStrings (doc, new Span(headEnd, extentEnd));
	  if (tokens.length > 0 && tokens[0] == ",")
	  	return new String[0];
	  return tokens;
	}

	// note this is different from getNgHead in Ace:  it does not
	// include possessive NPs
	public static Annotation getNgHead (Annotation ng) {
		Annotation hd = ng;
		while (true) {
			ng = (Annotation) hd.get("headC");
			if (ng == null) return hd;
			if (ng.get("cat") != "np") return hd;
			hd = ng;
		}
	}

	private static String[] getOfModifierTokens (Document doc,
			Annotation mention) {
	    Annotation of = (Annotation) mention.get("of");
	    if (of == null)
	    	return new String[0];
	    else
	    	return Tokenizer.gatherTokenStrings (doc, of.span());
	}

	/**
	 *  returns the concatenation of the String arrays 's1' and 's2'.
	 */

	private static String[] concat (String[] s1, String[] s2) {
		if (s1.length == 0)
			return s2;
		else if (s2.length == 0)
			return s1;
		else {
			String[] s = new String[s1.length + s2.length];
			for (int i=0; i<s1.length; i++)
			 	s[i] = s1[i];
			for (int i=0; i<s2.length; i++)
			 	s[i+s1.length] = s2[i];
			return s;
		}
	}

	/**
	 *  return true if a common noun phrase headed by 'mentionHead' is a possible
	 *  anaphoric reference to the (named) entity 'entity'.  Three cases
	 *  are allowed: <br>
	 *  1.  if the head is one of the words in the name of the entity
	 *      ("the Security Council" ... "the council")                <br>
	 *  2.  if the head is one of the words in the first mention of the entity
	 *      ("President Abe Lincoln" ... "the president")             <br>
	 *  3.  if the head is the name of a country or nationality, and the
	 *      head is "country", "nation", or "government"
	 */

	public static boolean nameNomCoref
			(Document doc, String det, String mentionHead, Annotation mention, Annotation entity) {
		//  this is Adam's condition (A):
		//  2003:  much worse for entities, slightly better for mentions
		//  2004:  minimal effect on entities, much better for relations
		if (det == null) return false;
		if (!(det.equals("the") || det.equals("this") || det.equals("that")))
			return false;
		//
		String[] entityName = (String[]) entity.get("name");
		if (entityName == null) return false;
		// don't resolve to "AP" == Associated Press!  -- ACE fudge
		if (entityName[0].equals("AP"))
			return false;
		// look for noun matching a word of name
		/*
		for (int i=0; i<entityName.length; i++)
			if (mentionHead.equalsIgnoreCase(entityName[i])) return true;
		// look for noun matching any token in first mention of entity;
		// this allows a match against any part of title or descriptor
		Vector mentions = (Vector) entity.get("mentions");
		Annotation firstMention = (Annotation) mentions.get(0);
		String[] fullName = Tokenizer.gatherTokenStrings(doc, firstMention.span());
		for (int i=0; i<fullName.length; i++)
			if (mentionHead.equalsIgnoreCase(fullName[i])) return true;
		*/
		if (nomInName(doc, mention, entity))
			return true;
		/*
		// look for generic noun matching name type
		String entityHead = (String) entity.get("nameType");
		if (entityHead.equals("PERSON"))
			return in(mentionHead, genericPersonTerms);
		else if (entityHead.equals("ORGANIZATION"))
			return in(mentionHead, genericOrganizationTerms);
		else if (entityHead.equals("GPE"))
			if (Ace.gazetteer.isNationality(entityName) || Ace.gazetteer.isCountry(entityName))
				return in(mentionHead, genericCountryTerms);
			else if (Ace.gazetteer.isState(entityName))
				return in(mentionHead, genericStateTerms);
			else if (Ace.gazetteer.isRegionOrContinent(entityName)) // a continent or region ... no general terms
				return false;
			else
				return in(mentionHead, genericGpeTerms);
		else if (entityHead.equals("LOCATION"))
			return in(mentionHead, genericLocationTerms);
		else if (entityHead.equals("FACILITY"))
			return in(mentionHead, genericFacilityTerms);
		else */
			return false;
	}

	public static boolean nomInName
			(Document doc, Annotation mention, Annotation entity) {
		String[] entityNameWithMods = (String[]) entity.get("nameWithMods");
		if (entityNameWithMods == null) return false;
		Annotation ngHead = Resolve.getNgHead(mention);
		String[] mentionTokens = Tokenizer.gatherTokenStrings(doc, ngHead.span());
		for (int i=0; i<mentionTokens.length; i++) {
			if (mentionTokens[i].equalsIgnoreCase("the")) continue;
			boolean match = false;
			for (int j=0; j<entityNameWithMods.length; j++) {
				if (mentionTokens[i].equalsIgnoreCase(entityNameWithMods[j])) {
					match = true;
					break;
				}
			}
			if (!match) return false;
		}
		return true;
	}
	// Adam's words
	private static final String[] genericPersonTerms =
		{"man", "human", "person", "individual", "gentleman", "fellow", "boy",
         "woman", "lady", "girl",  "official", "player", "diplomat",
         // tier 2
         "chairman", "officer", "executive",
         "leader", "lawyer", "friend", "father",
         "president", "spokesman", "governor",
         "coach", "attorney", "member", "director", "body"};
    private static final String[] genericOrganizationTerms =
    	{"academy", "administration", "agency", "airline", "army",
    	 "association", "board", "business", "college", "church",
    	 "company", "corporation", "establishment", "institute",
    	 "institution", "firm", "group",  "military", "office",
    	 "panel", "partnership", "party", "police", "school",
    	 "synagogue", "syndicate", "team", "trust",
    	 "union", "university", "organization",
    	 // tier 2 words
    	 "cathedral", "coalition", "temple", "bank",
         "commission", "committee",
         "council", "court", "department", "division",
         "federation", "force", "guild",  "industry", "mosque",
         "league", "parliament", "seminary", "society"};
    private static final String[] genericCountryTerms =
    	{"government", "country", "nation", "people", "kingdom"};
    private static final String[] genericStateTerms =
    	{"state"};
    private static final String[] genericGpeTerms =
    	{"village", "capital", "metropolis", "capital", "city",
       "town", "province"};
    private static final String[] genericLocationTerms =
    	{"area", "region",
    	// tier 2 words
    	"mount", "mountain", "hill", "ridge",
        "lake", "pond", "ocean", "sea", "river",
        "creek", "brook", "bayou", "stream"};
    private static final String[] genericFacilityTerms =
    	{"tower", "castle", "hotel", "palace", "hall", "house",
         "road", "route", "bridge", "pass", "tunnel",
         "home", "street", "stadium"};

  /**
   *  determines whether the named mention, with name 'mentionName' and NE type
   *  'mentionHead', can be coreferential with entity 'ent'.  The mention can
   *  have the same full name, a partial name, an abbreviation, or an acronym
   *  for the name of 'ent'.
   *
   *  @return an integer indicating the degree of name dissimilarity (0 for exact
   *          name match), or -1 to indicate no match
   */

	static int matchName (String[] mentionName, String mentionHead, Annotation ent) {
		int dissimilarity;
		String[] entityName = (String[]) ent.get("name");
		// name mention can only match entity with name
		if (entityName == null)
			return -1;
		String entityHead = (String) ent.get("nameType");
		if (nameTypeMatch && !mentionHead.equals(entityHead))
			return -1;
		dissimilarity = matchFullName (mentionName, mentionHead, entityName, entityHead);
		if (dissimilarity >= 0) return dissimilarity;
		// reverse -- shorter name first -- added Aug 7 2004
		dissimilarity = matchFullName (entityName, entityHead, mentionName, mentionHead);
		if (dissimilarity >= 0) return dissimilarity;
		if (mentionName.length == 1) {
			dissimilarity = isAcronym(entityName, mentionName[0]);
			if (dissimilarity >= 0) return dissimilarity;
			dissimilarity = isAbbreviation(entityName, mentionName[0]);
			if (dissimilarity >= 0) return dissimilarity;
		}
		/*
		// use strict acronym/abbreviation rules
		if (APFAnalyzer.isAcronym(entityName, mentionName[0])) return 0;
		if (APFAnalyzer.isAbbreviation(entityName, mentionName[0])) return 0;
		*/
		// allow for acronym/abbreviation preceding full name
		if (entityName.length == 1) {
			dissimilarity = isAcronym(mentionName, entityName[0]);
			if (dissimilarity >= 0) return dissimilarity;
			dissimilarity = isAbbreviation(mentionName, entityName[0]);
			if (dissimilarity >= 0) return dissimilarity;
		}
		return -1;
	}

	/**
	 *  returns true if 'mentionName' is a possible reference to 'entityName'.
	 *  The test succeeds if the tokens in 'mentionName' are a subset of those
	 *  in 'entityName', occurring in the same order as they do in 'entityName'.
	 *  Comparisons are done ignoring case because a name may appear in all caps
	 *  in the dateline (BAGHDAD vs. Baghdad) and may be capitalized differently
	 *  at the beginning of a sentence (Sergio de Mello vs. De Mello).
	 */

	public static int matchFullName (String[] mentionName, String mentionHead,
																	 String[] entityName,  String entityHead) {
		int j=0;
		for (int i=0; i<mentionName.length; i++) {
			while (j < entityName.length && !(mentionName[i].equalsIgnoreCase(entityName[j]))) {
				j++;
			}
			if (j >= entityName.length)
				return -1;
			j++;
		}
		if (mentionName.length < entityName.length) {
			if ((Ace.gazetteer.isNationality(mentionName) ||
			     Ace.gazetteer.isLocation(mentionName) ||
			     mentionHead == "GPE")
			    && entityHead != "PERSON") {
			  if (trace)
					System.out.println ("Refres: rejecting (location) " + concat(mentionName)
			                        + " as alias of " + concat(entityName));
				return -1;
			}
			if (trace)
				System.out.println ("Refres: recognizing " + concat(mentionName)
			  	                  + " as alias of " + concat(entityName));
		}
		return entityName.length - mentionName.length;
	}

	/**
	 *  returns true if 'acronym' is a possible acronym for 'name', such
	 *  as 'USA' for 'United States of America'.  The test
	 *  succeeds if the letters of 'acronym' are a subset of the initial
	 *  letters of the tokens of 'name', appearing in the same order as in
	 *  'name'.  The acronym must be at least 2 letters long.
	 */

	public static int isAcronym (String[] name, String acronym) {
		if (name.length < 2 || acronym.length() < 2)
			return -1;
		int iname=0;
		for (int i=0; i<acronym.length(); i++) {
			while (iname < name.length && 0 < name[iname].length() &&
			       !(name[iname].charAt(0) == acronym.charAt(i)))
				iname++;
			if (iname >= name.length) return -1;
			iname++;
		}
		if (trace) System.out.println ("Refres: recognizing " + acronym
			                             + " as acronym of " + concat(name));
		return name.length - acronym.length();
	}

	/**
	 *  returns true if 'abbrev' is an acronym-style abbreviation for 'name'
	 *  -- i.e., an acronym with periods, such as U.S.A. for 'United
	 *  States of America'.
	 */

	public static int isAbbreviation (String[] name, String abbrev) {
		if (name.length < 2 || abbrev.length() < 4 || abbrev.length() % 2 == 1)
			return -1;
		int iname=0;
		for (int i=0; i<(abbrev.length()/2); i++) {
			if (abbrev.charAt(2*i+1) != '.') return -1;
			while (iname < name.length &&
			       !(name[iname].charAt(0) == abbrev.charAt(2*i)))
				iname++;
			if (iname >= name.length) return -1;
			iname++;
		}
		if (trace) System.out.println ("Refres: recognizing " + abbrev
			                             + " as abbreviation of " + concat(name));
		return name.length - (abbrev.length() -2);
	}

	static private HashMap<String, String> nominative = new HashMap<String, String>();
	static {nominative.put("me", "i");
	        nominative.put("my", "i");
	        nominative.put("myself", "i");
	        nominative.put("your", "you");
	        nominative.put("yourself", "you");
	        nominative.put("him", "he");
	        nominative.put("his", "he");
	        nominative.put("himself", "he");
	        nominative.put("her", "she");
	        nominative.put("hers", "she");
	        nominative.put("herself", "she");
	        nominative.put("its", "it");
	        nominative.put("itself", "it");
	        nominative.put("us", "we");
	        nominative.put("our", "we");
	        nominative.put("ourselves", "we");
	        nominative.put("them", "they");
	        nominative.put("their", "they");
	        nominative.put("themselves", "they");
	     }
	static HashSet<String> reflexives = new HashSet<String>();
	static {reflexives.add("myself");
	        reflexives.add("yourself");
	        reflexives.add("himself");
	        reflexives.add("herself");
	        reflexives.add("itself");
	        reflexives.add("ourselves");
	        reflexives.add("themselves");
	      }

	 /**
	  *  returns the nominative form of 'pronoun'.
	  */

	 static String nominativeFormOf (String pronoun) {
	 	if (nominative.containsKey(pronoun))
			return nominative.get(pronoun);
		else
			return pronoun;
	}

	/**
	 *  return true if pronoun 'mentionHead' is a possible anaphor for
	 *  entity 'ent' (this also includes possessive pronouns of category
	 *  'det', and headless noun phrases of category 'np').
	 */

	public static boolean matchPronoun (Document doc, Annotation anaphor,
	                                    String mentionHead, Annotation ent) {
	  // cannot resolve pronoun against an entity whose sole mention is a
	  //   proper adjective
	  if (ent.get("properAdjective") != null)
	  	return false;
	  // don't resolve pronoun unless last mention was an NP or DET
	  Annotation lastMention = (Annotation) ent.get("lastMention");
	  String lastCat = (String) lastMention.get("cat");
	  if (lastCat != "np" && lastCat != "det" && lastCat != "ngroup")
	  	return false;
	  int entityPosition = ((Integer)ent.get("position")).intValue();
		int anaphorPosition = anaphor.span().start();
		// if (sentencesBetween(entityPosition, anaphorPosition) > 2) return false;
		String entityHead = (String) ent.get("head");
		String nominativeForm = nominativeFormOf(mentionHead);
		boolean match;
		if (nominativeForm == "he") {
			match = ent.get("human") != null &&
			        ent.get("number") == "singular" &&
			        ent.get("gender") != "female";
		} else if (nominativeForm == "she") {
			match = ent.get("human") != null &&
			        ent.get("number") == "singular" &&
			        ent.get("gender") != "male";
		} else if (nominativeForm == "it") {
			match = ent.get("human") == null &&
			        ent.get("number") == "singular";
		} else if (nominativeForm == "they") {
			match = ent.get("number") == "plural" || ent.get("nameType") == "ORGANIZATION" ||
			        ent.get("nameType") == "GPE";
		} else if (nominativeForm == "I" || nominativeForm == "i" ||
		           nominativeForm == "we" || nominativeForm == "you") {
		  // match = ent.get("human") != null; // used for ACE 2004
			match = nominativeForm.equals(entityHead);
		// we don't try to resolve headless NPs or pronominal 'one'
		// (if they appear in partitive constructs, 'both' and 'all' are
		//  handled as syntactically forced resolution).  'q' is the head
		//  for numbers ("five were hired").
		} else if (mentionHead == "some" || mentionHead == "either" ||
		           mentionHead == "neither" || mentionHead == "any" ||
		           mentionHead == "each" || mentionHead == "all" ||
		           mentionHead == "both" || mentionHead == "none" ||
		           mentionHead == "many" || mentionHead == "afew" ||
		           mentionHead == "most" || mentionHead == "q" ||
		           mentionHead == "one") {
		    match = false;
		// other pronouns which don't resolve with anything else
		} else if (mentionHead == "everyone" || mentionHead == "everything" ||
		           mentionHead == "everybody" || mentionHead == "nobody" ||
		           mentionHead == "noone" || mentionHead == "nothing" ||
		           mentionHead == "anyone" || mentionHead == "anything" ||
		           mentionHead == "anybody" || mentionHead == "someone" ||
		           mentionHead == "somebody" || mentionHead == "something" ||
		           mentionHead == "another") {
		    match = false;
		// also ignore deictics
		} else if (mentionHead == "this" || mentionHead == "these" ||
		           mentionHead == "that" || mentionHead == "those") {
		    match = false;
		} else {
			if (pronounsNotHandled.contains(mentionHead)) {
				System.err.println("Pronoun not being handled:  " + mentionHead);
				pronounsNotHandled.add(mentionHead);
			}
			match = false;
		}
		return match;
	}

	/**
	 *  replaces whitespace between tokens with a single blank.
	 */

	public static String normalizeName (String name) {
		StringTokenizer st = new StringTokenizer(name);
		StringBuffer result = new StringBuffer();
		while (st.hasMoreTokens()) {
			if(result.length() > 0) result.append(" ");
			result.append(st.nextToken());
		}
		return result.toString();
	}

	/**
	 *  append strings in 's', separated by blanks
	 */

	public static String concat (String[] s) {
		if (s.length == 0) return "";
		String r = s[0];
		for (int i=1; i<s.length; i++) r+=" " + s[i];
		return r;
	}

	/**
	 *  returns true if 'consit' is a name.
	 */

	public static boolean isName (Annotation constit) {
		return (constit != null) && (constit.get("cat") == "name");
	}

	/**
	 *  returns the head constituent associated with constituent 'ann'.
	 */

	public static Annotation getHeadC (Annotation ann) {
		while (ann.get("headC") != null) {
			ann = (Annotation) ann.get("headC");
		}
		return ann;
	}

	/**
	 *  returns the name associated with a noun phrase, as an array of token
	 *  strings, or null if the np does not have a name.
	 */

	public static String[] getNameTokens (Document doc, Annotation constit) {
		Annotation head = getHeadC(constit);
		if (head.get("cat") != "name") return null;
		return Tokenizer.gatherTokenStrings(doc,head.span());
	}

	public static String[] getHeadTokens (Document doc, Annotation constit) {
		Annotation head = getHeadC(constit);
		return Tokenizer.gatherTokenStrings(doc,head.span());
	}

	public static boolean in (Object o, Object[] array) {
		for (int i=0; i<array.length; i++)
			// if (array[i] == o) return true;
			if (array[i] != null && array[i].equals(o)) return true;
		return false;
	}

	public static boolean intersect (Object[] setA, Object[] setB) {
		if (setA.length == 0) return true;
		if (setB.length == 0) return true;
		for (int i=0; i<setA.length; i++)
			if (in(setA[i], setB)) return true;
		return false;
	}

	public static boolean equalArray (Object[] first, Object[] second) {
		if (first.length != second.length)
			return false;
		for (int i=0; i<first.length; i++)
			if (first[i] == null || !first[i].equals(second[i]))
			    return false;
		return true;
	}

}
