// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2001, 2003, 2005, 2006
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Refres;

import java.util.*;
import java.io.*;

import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Concepts.*;
import Jet.Zoner.SentenceSet;
import Jet.Pat.Pat;
import Jet.Parser.SynFun;
import Jet.Parser.ParseTreeNode;
import Jet.JetTest;
import Jet.Console;
import Jet.Control;
import Jet.Lex.Tokenizer;
import Jet.HMM.HMMNameTagger;
import Jet.MaxEntModel;
import AceJet.*;

/**
 *  contains procedures for reference resolution within a document.
 *  Name and noun resolution are rule based, using methods from class
 *  Resolve;  pronoun resolution is statistically based, using a 
 *  maximum-entropy classifier.
 */

public class MaxEntResolve {

	static Vector clauses;
	/**
	 *  all entities in the document.  Updated as new entities are created.
	 */
	static Vector entities;
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
	 *  the entity of the speaker/author, if identified by <SPEAKER> or <POSTER> tags
	 */
	private static Annotation speakerEntity = null;

	static HashMap mentionToEntity, syntacticAntecedent;

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
	 *  <CODE>Resolve.references</CODE> resolves the mentions (noun groups) in
	 *  <CODE>span</CODE> of Document <CODE>doc</CODE>.  It generates
	 *  <B>entity</B> annotations, corresponding to one or more mentions in the
	 *  document which are coreferential.  In addition, for every <B>event</B>
	 *  annotation in <CODE>span</CODE>, it generates an <B>r-event</B>
	 *  annotation in which each feature pointing to a mention is replaced by
	 *  the entity to which that mention has been resolved.
	 */

	public static void references (Document doc, Span span) {
		trace = Resolve.trace;
		if (pronounModel == null) {
			pronounModel = new MaxEntModel();
			String modelFile = JetTest.getConfigFile("Resolve.MaxEntModel.filename");
			if (modelFile == null) {
				System.err.println ("No Resolve.MaxEntModel.filename specified");
				System.exit (1);
			}
			pronounModel.loadModel(modelFile);
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
		Resolve.fullParse = fullParse;
		Vector mentions = Resolve.gatherMentions (doc, span);
		Vector clauses = Resolve.gatherClauses (doc, span);
		references (doc, span, mentions, clauses);
	}

	public static void references (Document doc, Span span, Vector mentions, Vector clauses) {
		// assumes features for constit/ngroup:  PA:[head  det  number human];  isName[binary]
		// assumes features for entity:  head;  name;  number;  human;  position;  mentions
		entities = doc.annotationsOfType("entity");
		if (entities == null) {
			entities = new Vector();
			speakerEntity = null;
		}
		mentionToEntity = new HashMap();

		if (trace) Console.println ("Resolving references");
		sentenceSet = new SentenceSet(doc);
		Resolve.sentenceSet = sentenceSet;
		Resolve.markMentions (mentions);
		syntacticAntecedent = Resolve.gatherSyntacticCoref (doc, mentions, clauses);
		for(int i = 0; i < mentions.size(); i++) {
			Annotation mention = (Annotation) mentions.get(i);
			resolveMention (doc, mention);
		}
		Resolve.updateEvents (doc, span, mentionToEntity);
	}

	/**
	 *  trains coreference from a set of files marked with <B>coref</B> tags.
	 *
	 *  @param directory   the directory containing the marked files
	 *  @param fileList    a list of text file names, one per line.
	 *  @limit limit       the maximum number of documents to be processed
	 */

	public static void train (String directory, String fileList, int limit) throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDocPath;
		while ((currentDocPath = reader.readLine()) != null) {
			docCount++;
			System.out.println ("\nProcessing file " + docCount + ": " + currentDocPath);
		  String textFile = directory + "/" + currentDocPath;
			ExternalDocument doc = new ExternalDocument("sgml", textFile);
			doc.setAllTags(true);
			doc.open();
			Control.processDocument (doc, null, false, 0);
			Ace.tagReciprocalRelations(doc);
			train (doc);
			if (docCount >= limit) break;
		}
	}
	
	/**
	 *  trains coreference from a document <CODE>doc</CODE> marked with
	 *  <B>coref</B> tags.
	 */

	public static void train (Document doc) {
		Vector sentences = doc.annotationsOfType("sentence");
		if (sentences == null) {
			System.err.println ("Cannot train reference resolution:  no sentences.");
			return;
		}
		entities = new Vector();
		mentionToEntity = new HashMap();
		sentenceSet = new SentenceSet(doc);
		Resolve.sentenceSet = sentenceSet;
		for (int i=0; i<sentences.size(); i++) {
			Annotation sentence = (Annotation) sentences.get(i);
			fullParse = sentence.get("parse") != null;
			Resolve.fullParse = fullParse;
			parseTree = (Annotation) sentence.get("parse");
			parents = SynFun.collectParents(parseTree);
			Vector mentions = Resolve.gatherMentions (doc, sentence.span());
			Vector clauses = Resolve.gatherClauses (doc, sentence.span());
			Resolve.markMentions (mentions);
			syntacticAntecedent = Resolve.gatherSyntacticCoref (doc, mentions, clauses);
			for(int j = 0; j < mentions.size(); j++) {
				Annotation mention = (Annotation) mentions.get(j);
				trainOnMention (doc, mention);
			}
		}
	}
	
	/**
	 *  add information on mention <CODE>mention</CODE> and its possible
	 *  antecedents to the training data which will be used to train the
	 *  coreference model.
	 */ 

	public static void trainOnMention (Document doc, Annotation mention) {
		// System.out.println ("trainOnMention " +  doc.text(mention)); //<<<
		ArrayList<Annotation> antecedents = null;
		if (fullParse)
		  antecedents = Hobbs.collectAntecedents (mention, parents, doc);
		Annotation headC = Resolve.getHeadC(mention);
		String cat = (String) headC.get("cat");
		// get entity id for this mention from coreference key
		// (if no coref tag or entity id, skip this mention)
		Vector corefs = doc.annotationsEndingAt(headC.end(), "mention");
		if (corefs == null || corefs.isEmpty())
			return;
		Annotation coref = (Annotation) corefs.get(0);
		String eid = (String) coref.get("entity");
		if (eid == null) {
			System.err.println ("mention tag for '" + doc.text(headC) + "' has no entity feature");
			return;
		}
		// get properties of anaphor ---------------------------------------------
		int mentionPosition = mention.span().start();
		String mentionHead = SynFun.getHead(doc, mention);
		if (mentionHead == null)
			return;
		String[] mentionName = Resolve.getNameTokens (doc, mention);
		boolean isNameMention = mentionName != null;
		boolean properAdjective = false;
		if (isNameMention) {
			boolean notNP = mention.get("cat") != "np";
			if (notNP && Ace.gazetteer.isNationality(mentionName))
				properAdjective = true;
			// should apply test only if adjective
			mentionName = Resolve.normalizeGazName(mentionName, notNP, trace);
		}
		Annotation bestEntity = null;

		// look for syntactically-determined antecedent
		// (these cases are not used to train resolver)
		if (false) { // syntacticAntecedent.containsKey(mention)) {
			if (trace) System.out.println ("Using syntactically-determined antecedent.");
			Annotation antecedent = (Annotation) syntacticAntecedent.get(mention);
			bestEntity = (Annotation) mentionToEntity.get(antecedent);
			if (bestEntity == null) {
				System.err.println ("Resolve:  syntactic antecedent not in entity");
				System.err.println ("          mention:    " + doc.text(mention));
				System.err.println ("          antecedent: " + doc.text(antecedent));
			}
		// else iterate over antecedents, generating training data
		} else {
			for (int ie=0; ie<entities.size(); ie++) {
				int dissimilarity = 0;
				Annotation ent = (Annotation) entities.elementAt(ie);
				boolean match = eid.equals(ent.get("eid"));
				if (isNameMention) {
					// train name resolver
				} else if (cat == "pro" || cat == "det" || cat == "np") {
					String pronoun = mentionHead.toLowerCase().intern();
					trainPronounResolver (doc, mention, pronoun, ent, match, fullParse, antecedents);
				// adj and ven (headless np's) are treated like n for resolution
				// v and tv heads for np's are assumed to be tagging errors
				// adv is for 'here', 'there', 'abroad' (perfectMentions only)
				} else if (cat == "n" || cat == "adj" || cat == "ven" || cat == "v" ||
				           cat == "tv" || cat == "hyphword" || cat == "title" ||
				           cat == "nnp" || cat == "nnps" || cat == "adv") {
					// train nom resolver (doc, mention, ent);
				} else if (cat == "$") {
					//
				} else if (cat == "q") {
					//
				} else {
					System.err.println ("Unexpected head cat " + cat + " for " + doc.text(mention));
					//
					break;
				}
				if (match) {
					bestEntity = ent;
				}
			}
		}

		// if no suitable antecedent is found, create a new entity
		if (bestEntity == null) {
			bestEntity =
				Resolve.createNewEntity (doc, mention, mentionHead, properAdjective, entities);
			bestEntity.put("eid", eid);
			// System.err.println ("Assigning eid " + eid + " to " + mentionHead);
		} else {
			if (bestEntity.get("properAdjective") != null && !properAdjective)
					bestEntity.put("properAdjective", null);
			if (trace) Console.println ("Resolving " + doc.text(mention) + " to " +
			                            doc.text(bestEntity));
		}
		Resolve.addMentionToEntity (doc, mention, mentionHead, mentionName,
		                            bestEntity, mentionToEntity);
	}

	static int pronounDefiniteNonCorefSuccessCount = 0;
	static int pronounDefiniteNonCorefFailureCount = 0;
	
	/**
	 *  train the pronoun resolver using the information about pronominal
	 *  mention <CODE>mention</CODE> and one of its possible antecedents.
	 *
	 *  @param doc      the Document containing the pronoun
	 *  @param mention  the pronominal mention (anaphor)
	 *  @param pronoun  the pronoun
	 *  @param entity   a possible antecedent of the pronoun
	 *  @param match    'true' if 'entity' is the correct antecedent of the pronoun
	 *  @param parse    'true' if the document has been parsed
	 *  @param antecedents  the possible antecedents of the pronoun
	 */

	private static void trainPronounResolver (Document doc, Annotation mention,
			String pronoun, Annotation entity, boolean match,
			boolean parse, ArrayList<Annotation> antecedents) {
		boolean reflexive = Resolve.reflexives.contains(pronoun);
		String nominativeForm = Resolve.nominativeFormOf(pronoun);
		if (! (nominativeForm == "he" || nominativeForm == "she" ||
		       nominativeForm == "it" || nominativeForm == "they"))
			return;
		if (pronounDefiniteNonCoref (nominativeForm, entity)) {
			if (match) {
				System.err.println ("Error in pronounDefiniteNonCoref, pronoun = " +
				                     pronoun + ", antecedent = " + doc.text(entity));
				pronounDefiniteNonCorefFailureCount++;
			} else {
				pronounDefiniteNonCorefSuccessCount++;
			}
			return;
		}
		Datum d = pronounFeatures (doc, mention, nominativeForm, reflexive,
		  entity, parse, antecedents);
	  d.setOutcome (match ? "T" : "F");
	  int distance = Resolve.distance (doc, entity, mention, parse, antecedents); //<<<
	  // if (match && distance >= 8)                                                 //<<<
	  // 	System.out.println ("match " + doc.text(mention) + ":" + doc.text(entity) + " " + d); //<<<
		//   record
		pronounModel.addEvent(d);
		return;
	}

	/**
	 *  returns true if pronoun 'nominativeForm' can never be an antecedent
	 *  of entity 'ent'.  Enforces constraints such as number, (non)human,
	 *  and gender agreement of antecedent and anaphor.  Such cases are not
	 *  used to train the statistical model.
	 */

	private static boolean pronounDefiniteNonCoref (String nominativeForm,
			Annotation ent) {
		boolean match;
		String nameType = (String) ent.get("nameType");
		//      (need toUpperCase because 'perfectparses' uses lower case 'ORGANIZATION')
		if (nameType != null)
			nameType = nameType.toUpperCase().intern();
		String AceTypeSubtype = (String) ent.get("ACEtype");
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
		} else /* if (nominativeForm == "they") */ {
			match = ent.get("number") == "plural" || nameType == "ORGANIZATION" ||
			        nameType == "GPE" || 
				(AceTypeSubtype != null && AceTypeSubtype.equals("PERSON:Group"));
		}
		return !match;
	}

	private static Datum pronounFeatures (Document doc, Annotation mention, String pronoun,
			boolean reflexive, Annotation entity, boolean parse, ArrayList<Annotation> antecedents) {
		//   build feature entry
		Datum d = new Datum();
		Annotation lastMention = (Annotation) entity.get("lastMention");
		String lastCat = (String) lastMention.get("cat");
		//   -- Hobbs distance from last mention
		//      (if not parsing, character distance / 25)
		if (reflexive) {
			boolean sameSimplex = Hobbs.sameSimplex(lastMention , mention, parents);
			if (parse && sameSimplex) {
				d.addFV ("reflexdist", "sameSimplex");
			} else {
				int distance = Resolve.distance (doc, entity, mention, parse, antecedents);
				if (distance == Hobbs.SAME_SENTENCE) {
					d.addFV ("reflexdist", "sameSent");
				} else {
					d.addFV ("reflexdist", Math.min(distance / (useParser ? 1 : 25),40) + "");
				}
			}
		} else  /* non-reflexive pronoun */ {
			int distance = Resolve.distance (doc, entity, mention, parse, antecedents);
			if (distance == Hobbs.SAME_SENTENCE) {
				d.addFV ("dist", "sameSent");
			} else {
				d.addFV ("dist", Math.min(distance / (useParser ? 1 : 25),40) + "");
			}
			Annotation anteHeadC = Resolve.getHeadC(lastMention);
			String anteHead = SynFun.getHead(doc, anteHeadC);
			d.addFV ("heads", anteHead + ":" + pronoun);
			if (lastMention.get("subject-1") != null)
				d.addF("subject");
			d.addFV ("lastCat", lastCat);
		}
		//   -- number of prior mentions of antecedent
		int priorMentions = ((Vector) entity.get("mentions")).size();
		d.addFV ("prior", Math.min(priorMentions, 10) + "");
		/*   -- head:pronoun
		String entityHead = (entity.get("nameType") != null) ?
		                    (String) entity.get("nameType") :
		                    (String) entity.get("head");
		d.addF (entityHead + ":" + pronoun);
		*/
		return d;
	}

	/**
	 *  perform anaphora resolution on 'mention' in Document 'doc'.
	 *  either add it to an existing entity, or create a new entity.
	 */

	private static void resolveMention (Document doc, Annotation mention) {
		ArrayList<Annotation> antecedents = null;
		if (fullParse)
		  antecedents = Hobbs.collectAntecedents (mention, parents, doc);
		Annotation headC = Resolve.getHeadC(mention);
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
		String[] mentionName = Resolve.getNameTokens (doc, mention);
		boolean isNameMention = mentionName != null;
		boolean properAdjective = false;
		if (isNameMention) {
			boolean notNP = mention.get("cat") != "np";
			if (notNP && Ace.gazetteer.isNationality(mentionName))
				properAdjective = true;
			// should apply test only if adjective
			mentionName = Resolve.normalizeGazName(mentionName, notNP, trace);
		}
		Annotation bestEntity = null;
		float bestProbability = 0;
		// look for syntactically-determined antecedent ----------------------------
		if (syntacticAntecedent.containsKey(mention) && !Ace.perfectEntities) {
			if (trace) System.out.println ("Using syntactically-determined antecedent.");
			Annotation antecedent = (Annotation) syntacticAntecedent.get(mention);
			bestEntity = (Annotation) mentionToEntity.get(antecedent);
			if (bestEntity == null) {
				System.err.println ("Resolve:  syntactic antecedent not in entity");
				System.err.println ("          mention:    " + doc.text(mention));
				System.err.println ("          antecedent: " + doc.text(antecedent));
			} else {
				bestProbability = 1;
			}
		} else if ((cat == "pro" || cat == "det") &&
		           Resolve.nominativeFormOf(mentionHead.toLowerCase()).equals("i") &&
		           speakerEntity != null) {
			bestEntity = speakerEntity;
			bestProbability = 1;
		// else search for antecedent, taking most recently mentioned matching entity --
		} else {
			for (int ie=0; ie<entities.size(); ie++) {
				float prob;
				Annotation ent = (Annotation) entities.elementAt(ie);
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
					boolean match = ent.get("entityID") != null && ent.get("entityID").equals(eid);
					prob = match ? 1 : 0;
				} else if (isNameMention) {
					int dissimilarity = Resolve.matchName (mentionName, mentionHead, ent);
					boolean match = dissimilarity >= 0;
					prob = match ? (1000.f / (1000 + Resolve.distance (doc, ent, mention, fullParse, antecedents))) : 0;
				// current speaker or poster is not referred to except by "I" or by name
				// (in a subsequent speaker or poster tag)
				} else if (ent == speakerEntity) {
					continue;
				} else if (cat == "pro" || cat == "det" || cat == "np") {
					String pronoun = mentionHead.toLowerCase().intern();
					prob = matchPronoun (doc, mention, pronoun, ent, fullParse, antecedents);
				// adj and ven (headless np's) are treated like n for resolution
				// v and tv heads for np's are assumed to be tagging errors
				// adv is for 'here', 'there', 'abroad' (perfectMentions only)
				} else if (cat == "n" || cat == "adj" || cat == "ven" || cat == "v" ||
				           cat == "tv" || cat == "hyphword" || cat == "title" ||
				           cat == "nnp" || cat == "nnps" || cat == "adv") {
					boolean match = Resolve.matchNom (doc, mention, ent);
					prob = match ? (1000.f / (1000 + Resolve.distance (doc, ent, mention, fullParse, antecedents))) : 0;
				} else if (cat == "$") {
					prob = 0;
				} else if (cat == "q") {
					prob = 0;
				} else {
					System.err.println ("Unexpected head cat " + cat + " for " + doc.text(mention));
					prob = 0;
					break;
				}
				if (prob > bestProbability) {
					bestProbability = prob;
					bestEntity = ent;
				}
			}
		}

		// if no suitable antecedent is found, create a new entity
		if (bestProbability < 0.01) {
			bestEntity =
				Resolve.createNewEntity (doc, mention, mentionHead, properAdjective, entities);
		} else {
			if (bestEntity.get("properAdjective") != null && !properAdjective)
					bestEntity.put("properAdjective", null);
			if (trace) Console.println ("Resolving " + doc.text(mention) + " to " +
			                            doc.text(bestEntity));
		}
		Resolve.addMentionToEntity (doc, mention, mentionHead, mentionName,
		                            bestEntity, mentionToEntity);
		// if mention is inside <POSTER> or <SPEAKER> tags, record as speakerEntity
		Span span = mention.span();
		if (HMMNameTagger.inZone(doc, span, "POSTER") || HMMNameTagger.inZone(doc, span, "SPEAKER")) {
			speakerEntity = bestEntity;
		}
	}

	/**
	 *  return the probability that pronoun 'pronoun' is a possible anaphor for
	 *  entity 'ent' (this also includes possessive pronouns of category
	 *  'det', and headless noun phrases of category 'np').
	 */

	public static float matchPronoun (Document doc, Annotation anaphor,
	                                  String pronoun, Annotation entity,
	                                  boolean parse, ArrayList<Annotation> antecedents) {
	  // cannot resolve pronoun against an entity whose sole mention is a
	  //   proper adjective
	  if (entity.get("properAdjective") != null)
	  	return 0;
	  String nominativeForm = Resolve.nominativeFormOf(pronoun);
	  if (nominativeForm == "I" || nominativeForm == "i" ||
		    nominativeForm == "we" || nominativeForm == "you")
			return nominativeForm.equals(entity.get("head")) ? 1 : 0;
		if (! (nominativeForm == "he" || nominativeForm == "she" ||
		       nominativeForm == "it" || nominativeForm == "they"))
			return 0;
		// if definitely non-coreferential (feature mis-match), return 0
		if (pronounDefiniteNonCoref(nominativeForm, entity))
			return 0;
		boolean reflexive = Resolve.reflexives.contains(pronoun);
		Datum d = pronounFeatures (doc, anaphor, nominativeForm, reflexive, entity, parse, antecedents);
		return (float) pronounModel.prob(d, "T");
	}

	static String trainingDirectory1 = "coref";
	static String trainingCollection1 = "coref/training nwire coref.txt";
	static String trainingDirectory = "coref/2005-co";
	static String trainingCollection = "coref/2005-co/bcbnnwCo.txt";
	static String trainingDirectoryParses = "coref/2005-co-parsed";
	static String trainingCollectionParses = "coref/2005-co/bcbnnwCo.txt";

	static boolean useParser = true;

	static MaxEntModel pronounModel;
	
	/**
	 *  trains the pronoun coreference model from a training collection
	 *  of coreference-annotated documents.
	 */
	 
	public static void main (String[] args) throws IOException {
		if (useParser) {
			JetTest.initializeFromConfig("props/ace 05 use parses noresolve.properties");
		} else {
			JetTest.initializeFromConfig("props/ME ace 05 noresolve.properties");
		}
		// create pronoun model
		pronounModel =
			useParser ?
			new MaxEntModel ("data/pronounCorefFeaturesP.txt",
	                     "data/pronounCorefModelP.txt")
			:
			new MaxEntModel ("data/pronounCorefFeatures.txt",
	                     "data/pronounCorefModel.txt");
		// load ACE type dictionary
		AceJet.EDTtype.readTypeDict();
		Resolve.ACE = true;
		if (useParser) {
			train (trainingDirectoryParses, trainingCollectionParses, 300);
		} else {
			train (trainingDirectory, trainingCollection, 300);
		}
		pronounModel.buildModel();
		pronounModel.saveModel();
		System.out.println
			("pronounDefiniteNonCorefSuccessCount = " + pronounDefiniteNonCorefSuccessCount);
		System.out.println
			("pronounDefiniteNonCorefFailureCount = " + pronounDefiniteNonCorefFailureCount);
	}

}
