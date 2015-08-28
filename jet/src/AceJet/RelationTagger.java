// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.60
//Copyright:    Copyright (c) 2011, 2013
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package AceJet;

import java.util.*;
import java.io.*;
import Jet.*;
import Jet.Parser.SynFun;
import Jet.Parser.ParseTreeNode;
import Jet.Refres.Resolve;
import Jet.Lisp.*;
import Jet.Pat.Pat;
import Jet.Tipster.*;
import Jet.Chunk.Chunker;
import Jet.Scorer.NameTagger;
import Jet.Zoner.SentenceSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  procedures for training the ACE relation tagger and for tagging new text.
 *  Currently the tagger uses a set of 4 maximum entropy models:
 *  <ol>
 *  <li> a detection model for relations within base noun phrases
 *  <li> a classification model for relations within base noun phrases
 *  <li> a detection model for relations between other entity pairs
 *  <li> a classification model for relations between other entity pairs
 *  </ol>
 *  The four feature files and four model files are stored together in
 *  a single model directory.  This directory may also have an "argTypes"
 *  file, specifying the allowed argument types for each relation type
 *  and subtype.
 *  <p>
 *  This relation tagger is based on maximum entropy models and is a much
 *  faster replacement for the nearest-neighbor model used until 2011.
 */

public class RelationTagger {

	final static Logger logger = LoggerFactory.getLogger(RelationTagger.class);

	static boolean useParser = false;
	static Document doc;
	static AceDocument aceDoc;
	static String currentDoc;
	static NameTagger realNameTagger;
	// the set of sentences in doc
	static SentenceSet sentences;
	static final String[] relations = {"of", "poss", "nameMod"};

	// mapping from mention ID to Mention
	static HashMap mentionIDMap;
	// mapping from start of head to Mention
	static HashMap mentionHeadMap;
	// mapping from start of extent to Mention
	static HashMap mentionStartMap;
	// set of mentions (excluding generic)
	static Set<AceEntityMention> mentionSet;
	// set of all mentions, including generics
	static TreeSet<AceEntityMention> allMentionSet;
	// list of relation mentions
	//   for training:  taken from APF file, deleted as used
	static ArrayList<AceRelationMention> relMentionList;
	static List<AceRelation> relationList;
	// name of current doc (used in generating ID's)
	static String docName;
	// set true to expand conjuncts
	static final boolean expandConjuncts = true;
	// map from a mention to following conjunct
	static HashMap conjunctf = new HashMap();
	// map from a mention to prior conjunct
	static HashMap conjunctb = new HashMap();
	// maxEntModels
	static MaxEntModel model0 = null;
	static MaxEntModel model1 = null;
	static MaxEntModel model2 = null;
	static MaxEntModel model3 = null;
	// type constraints on arguments
	static Map<String, String> typeConstraints = null;
	// if true, generate 'sameSentence' relations
	static boolean tagSameSentence = false;

	/**
	 *  trains a new relation tagger. Takes 5n+2 arguments: <br>
	 *  RelationTagger configFile [docList textDir textSuffix apfDir apfSuffix] modelDirectory <br>
	 *  where arguments 2 through 6 are repeated for each training corpus (training data from
	 *  multiple corpora can be combined into a single model).
	 *  The resulting maxent models are written into 'modelDirectory'.
	 */

	public static void main (String[] args) throws IOException {
		if (!(args.length == 7 || args.length == 12)) {
			logger.error ("RelationTagger requires 5n+2 arguments:");
			logger.error ("  configFile [docList textDir textSuffix apfDir apfSuffix]+ modelDirectory");
			System.exit (1);
		}
		String configFile = args[0];
		String modelDirectory = args[args.length - 1];

		// initialize Jet
		logger.info ("Learning relations ...");
		JetTest.initializeFromConfig (configFile);
		Ace.setAceYear ();
		realNameTagger = JetTest.nameTagger;
		Pat.trace = false;
		Resolve.trace = false;
		Resolve.ACE = true;
		Ace.perfectMentions = true;
		Ace.perfectEntities = true;

		model0 = new MaxEntModel(modelDirectory + "/" + "featureFile-0", modelDirectory + "/" + "modelFile-0");
		model1 = new MaxEntModel(modelDirectory + "/" + "featureFile-1", modelDirectory + "/" + "modelFile-1");
		model2 = new MaxEntModel(modelDirectory + "/" + "featureFile-2", modelDirectory + "/" + "modelFile-2");
		model3 = new MaxEntModel(modelDirectory + "/" + "featureFile-3", modelDirectory + "/" + "modelFile-3");
		model0.initializeForTraining();
		model1.initializeForTraining();
		model2.initializeForTraining();
		model3.initializeForTraining();
		for (int iarg = 1; iarg < args.length -1; iarg += 5) {
			String docList = args[iarg];
			String textDir = args[iarg + 1];
			String textSuffix = args[iarg + 2];
			String apfDir = args[iarg + 3];
			String apfSuffix = args[iarg + 4];
			learnFromFileList (docList, textDir, textSuffix, apfDir, apfSuffix);
		}
		model0.buildModel();
		model1.buildModel();
		model2.buildModel();
		model3.buildModel();
		model0.saveModel();
		model1.saveModel();
		model2.saveModel();
		model3.saveModel();
	}

	static void learnFromFileList (String docList, String textDir, String textFileSuffix, String apfDir, String apfFileSuffix) 
		throws IOException {
		// open list of files
		BufferedReader reader = new BufferedReader (new FileReader(docList));
		int docCount = 0;
		while ((currentDoc = reader.readLine()) != null) {
			// process file 'currentDoc'
			docCount++;
			logger.info ("\nProcessing document " + docCount + ": " + currentDoc);
			// read document
			String textFile;
			textFile = textDir + "/" + currentDoc + "." + textFileSuffix;
			ExternalDocument xdoc = new ExternalDocument("sgml", textFile);
			xdoc.setAllTags(true);
			xdoc.open();
			doc = xdoc;
			// read key file with relation information
			readACErelations (textFile, apfDir + "/" + currentDoc + "." + apfFileSuffix);
			// process document (unless starting from parse, identify named
			//                   entities, syntactic structures, and coreference)
			Ace.monocase = Ace.allLowerCase(doc);
                        PerfectAce.buildEntityMentionMap (xdoc, aceDoc);
                        JetTest.nameTagger = new PerfectNameTagger(aceDoc, realNameTagger);
			Control.processDocument (doc, null, docCount < 0, docCount);
			sentences = new SentenceSet(doc);
			// iterate over syntactic relations, record candidates for ACE relations
			findSyntacticRelations (doc);
			// collect all pairs of nearby mentions
			List<AceEntityMention[]> pairs = findMentionPairs ();
			// iterate over pairs of adjacent mentions, record candidates for ACE relations
			for (AceEntityMention[] pair : pairs)
				addTrainingInstance (pair[0], pair[1]);
			// were any positive instances not captured?
			reportLeftovers ();
		}
	}

	/**
	 *  relation 'decoder':  using previously trained models, identifies
	 *  the relations in document 'doc' (from file name 'currentDoc') and adds them
	 *  as AceRelations to AceDocument 'aceDoc'.
	 */

	public static void findRelations (String currentDoc, Document d, AceDocument ad) {
		doc = d;
		aceDoc = ad;
		docName = currentDoc;
		sentences = new SentenceSet(doc);
		relationList = new ArrayList<AceRelation> ();
		findEntityMentions (aceDoc);
		// record conjunct links for mentions
		findConjuncts (doc);
		// iterate over syntactic relations, record candidates for ACE relations
		findSyntacticRelations (doc);
		// collect all pairs of nearby mentions
		List<AceEntityMention[]> pairs = findMentionPairs ();
		// iterate over pairs of adjacent mentions, using model to determine which are ACE relations
		for (AceEntityMention[] pair : pairs) {
			boolean reln = predictRelation (pair[0], pair[1]);
			if (!reln && tagSameSentence)
				linkNeighbors (pair[0], pair[1]);
		}
		//
		if (expandConjuncts)
			extendRelationsToConjuncts (); 
		// combine relation mentions into relations
		relationCoref (aceDoc);
		removeRedundantMentions (aceDoc);
	}

	/**
	 *  if both m1 and m2 are name mentions, adds a 'sameSentence' relation between m1 and m2.
	 */

	private static void linkNeighbors (AceEntityMention m1, AceEntityMention m2) {
		if (m1.type != "NAME" || m2.type != "NAME") return;
		String type = "sameSentence";
		String subtype = m1.entity.type + ":" + m2.entity.type;
		AceRelationMention mention = new AceRelationMention("", m1, m2, doc);
		AceRelation relation = new AceRelation("", type, subtype, "", m1.entity, m2.entity);
		relation.addMention(mention);
		relationList.add(relation);
	}

	/**
	 *  load the maxent models used by the relation tagger.  Must be invoked
	 *  before calling 'findRelations'.
	 */

	static void loadModels (String modelDirectory) {
		model0 = new MaxEntModel();
		model1 = new MaxEntModel();
		model2 = new MaxEntModel();
		model3 = new MaxEntModel();
		model0.loadModel(modelDirectory + "/" + "modelFile-0");
		model1.loadModel(modelDirectory + "/" + "modelFile-1");
		model2.loadModel(modelDirectory + "/" + "modelFile-2");
		model3.loadModel(modelDirectory + "/" + "modelFile-3");
		loadTypeConstraints (modelDirectory + "/" + "argTypes");
	}

	static void loadTypeConstraints (String fileName) {
		typeConstraints = new HashMap<String, String> ();
		try {
			BufferedReader reader = new BufferedReader (new FileReader (fileName));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] fields = line.split("\\s+", 3);
				typeConstraints.put(fields[0] + "." + fields[1], fields[2]);
			}
		} catch (IOException e) {
			logger.error ("Error opening relation type constraint file");
			logger.error ("  " + e);
			logger.error ("  Type constraints will not be enforced for relations");
		}
	}

	/**
	 *  returns 'true' if argument 'argNo' (which should be either 'arg1' or 'arg2') with ACE
	 *  entity type 'argType' is a valid argument type for relations of type 'relationType'
	 *  and subtype 'relationSubtype'.  Information on valid types is obtained from file
	 *  'argTypes';  if argTypes is absent, this method always returns true.
	 */

	static boolean satisfiesTypeConstraint (String relationType, String relationSubtype, String argNo,
			String argType) {
		if (typeConstraints.isEmpty())
			return true;
		argType = argType.substring(0, 3);
		String key = relationType + "." + relationSubtype + "." + argNo;
		String allowedTypes = typeConstraints.get(key);
		if (allowedTypes == null) {
			logger.warn ("No relation type constraint for " + key);
			return true;
		}
		boolean v = allowedTypes.contains(argType);
		if (!v) {
			logger.trace ("Relation type violation for {}", key);
			logger.trace ("  arg is {}, allowed types are {}", argType, allowedTypes);
		}
		return v;
	}

	static Map<String, String> syntacticRelationMap;

	/**
	 *  iterate over all syntactic relations;  record relations between mentions
	 *  in syntacticRelationMap.
	 */

	private static void findSyntacticRelations (Document doc) {
		syntacticRelationMap  = new HashMap<String, String> ();
		Vector<Annotation> constits = doc.annotationsOfType("constit");
		if (constits != null) {
			for (Annotation ann : constits) {
				for (String relation : relations) {
					if (ann.get(relation) != null) {
						Annotation value = (Annotation) ann.get(relation);
						recordSyntacticRelation (ann, relation, value);
					}
				}
				Annotation subject = (Annotation) ann.get("subject");
				Annotation vp = (Annotation) ann.get("headC");
				if (subject != null && vp != null && vp.get("object") != null) {
					Annotation object = (Annotation) vp.get("object");
					String verb = SynFun.getNameOrHead(doc, vp);
					recordSyntacticRelation (subject, verb, object);
				}
				/*
				String verb = SynFun.getImmediateHead(ann);
				if (verb != null) {
					Annotation subject = (Annotation) ann.get("subject");
					Annotation vp = (Annotation) ann.get("headC");
					if (vp != null && vp.get("object") != null) {
						Annotation object = (Annotation) vp.get("object");
						String verb = SynFun.getNameOrHead(doc, vp);
						recordSyntacticRelation (subject, verb, object);
					}
					Annotation pp = (Annotation) ann.get("pp");
					if (pp != null) {
						Annotation[] ppChildren = ParseTreeNode.children(pp);
						if (ppChildren != null & ppChildren.length == 2) {
							Annotation pNode = ppChildren[0];
							Annotation pObject = ppChildren[1];
							String p = SynFun.getHead(doc, pNode);
							if (subject != null)
								recordSyntacticRelation (subject, "s-" + verb + "-" + p, pObject);
							if (object != null)
								recordSyntacticRelation (object, "o-" + verb + "-" + p, pObject);
						}
					}
				}
				*/
			}
		}
	}

	/**
	 *  given a syntactic relation between two constituents, arg1 and arg2,
	 *  look for corresponding mentions;  if found, record the relation and
	 *  the pair of mentions in syntacticRelationMap.
	 */

	private static void recordSyntacticRelation
		(Annotation arg1, String relation, Annotation arg2) {
		logger.trace ("recordSyntacticRelation relation = {}", relation);
		logger.trace ("                        arg1 = {}  arg2 = {}",
			 doc.normalizedText(arg1), doc.normalizedText(arg2));
		// for each argument (constituent), find correponding Mention (if any)
		Annotation arg1Head = Resolve.getHeadC (arg1);
		Span span1 = arg1Head.span();
		int start1 = span1.start();
		AceEntityMention m1 = (AceEntityMention) mentionHeadMap.get(new Integer(start1));
		if (m1 == null) return;
		Annotation arg2Head = Resolve.getHeadC (arg2);
		Span span2 = arg2Head.span();
		int start2 = span2.start();
		AceEntityMention m2 = (AceEntityMention) mentionHeadMap.get(new Integer(start2));
		if (m2 == null) return;
		syntacticRelationMap.put(m1.id + ":" + m2.id, relation);
	}
		// if two mentions co-refer, they can't be in a relation
		// if (!canBeRelated(m1, m2)) return;
		// if (m1.compareTo(m2) < 0) {
			// ArrayList rels = recordCandidateWithConjuncts (m1, m2);
			// recordSyntacticLink(rels, relation);
		// } else {
			// ArrayList rels = recordCandidateWithConjuncts (m2, m1);
			// recordSyntacticLink(rels, relation + "-1");
		// }
	// }

	private static boolean canBeRelated (AceEntityMention m1, AceEntityMention m2) {
		if (m1.entity.id.equals(m2.entity.id)) return false;
		return true;
	}

	private static final int mentionWindow = 4;

	/**
	 *  returns the set of all pairs of mentions separated by at most mentionWindow mentions
	 */

	static List<AceEntityMention[]> findMentionPairs () {
		List<AceEntityMention[]> pairs = new ArrayList<AceEntityMention[]> ();
		if (mentionSet.isEmpty()) return pairs;
		ArrayList mentionList = new ArrayList(mentionSet);
		for (int i=0; i<mentionList.size()-1; i++) {
			for (int j=1; j<=mentionWindow && i+j<mentionList.size(); j++) {
				AceEntityMention m1 = (AceEntityMention) mentionList.get(i);
				AceEntityMention m2 = (AceEntityMention) mentionList.get(i+j);
				// if two mentions co-refer, they can't be in a relation
				if (!canBeRelated(m1, m2)) continue;
				// if two mentions are not in the same sentence, they can't be in a relation
				if (!sentences.inSameSentence(m1.jetHead.start(), m2.jetHead.start())) continue;
				pairs.add(new AceEntityMention[] {m1, m2});
			}
		}
		return pairs;
	}

	/**
	 *  check whether there is a relation between m1 and m2 in the training corpus;
	 *  update the maxent models with the relation type (or the absence of a relation).
	 */

	private static void addTrainingInstance (AceEntityMention m1, AceEntityMention m2) {
		// generate features
		Datum d = relationFeatures(m1, m2);
		// retrieve tag
		String outcome = "nil";
		loop:
		for (AceRelationMention mention : relMentionList) {
			if (mention.arg1 == m1 && mention.arg2 == m2) {
				outcome = mention.relation.type + ":" + mention.relation.subtype;
				relMentionList.remove(mention);
				break loop;
			} else if (mention.arg1 == m2 && mention.arg2 == m1) {
				outcome = mention.relation.type + ":" + mention.relation.subtype + "-1";
				relMentionList.remove(mention);
				break loop;
			}
		}
		boolean local = inSameBaseNP(m1.head, m2.head);
		MaxEntModel identificationModel = local ? model0 : model2;
		MaxEntModel classificationModel = local ? model1 : model3;
		if (outcome == "nil") {
			d.setOutcome("nil");
			identificationModel.addEvent(d);
		} else {
			d.setOutcome("t");
			identificationModel.addEvent(d);
			d.setOutcome(outcome);
			classificationModel.addEvent(d);
		}
	}

	/**
	 *  features from ZHOU GuoDong, SU Jia, Zhang Jie and ZHANG Min
	 *  ACL 2005
	 */

	private static Datum relationFeatures (AceEntityMention m1, AceEntityMention m2) {
		Datum d = new Datum();

		// spans of base NPs
		Span m1span = new Span(m1.jetExtent.start(), m1.jetHead.end());
		Span m2span = new Span(m2.jetExtent.start(), m2.jetHead.end());
		// head tokens
		String[] headTokens1 = tokensIn(m1.jetHead.start(), m1.jetHead.end()).toArray(new String[0]);
		String[] headTokens2 = tokensIn(m2.jetHead.start(), m2.jetHead.end()).toArray(new String[0]);
		// words between mentions
		List<String> wb = tokensIn(m1span.end(), m2span.start());
		int wbCount = wb.size();
		// chunk path between mentions
		ChunkPath cp = new ChunkPath(doc, m1span.end(), m2span.start());
		int cpCount = cp.size();

		// word features
		for (String s : headTokens1)
			d.addFV("wm1", s);
		// >>> replaceAlls make score worse
		// d.addFV("hm1", m1.headText.replace(" ","_"));
		d.addFV("hm1", m1.headText.replaceAll("\\s+","_"));
		for (String s : headTokens2)
			d.addFV("wm2", s);
		// d.addFV("hm2", m2.headText.replace(" ","_"));
		d.addFV("hm2", m2.headText.replaceAll("\\s+","_"));
		// String hm12 = m1.headText.replace(" ","_") + ":" + m2.headText.replace(" ","_");
		String hm12 = m1.headText.replaceAll("\\s+","_") + ":" + m2.headText.replaceAll("\\s+","_");
		d.addFV("hm12", hm12);
		if (wbCount == 0) {
			d.addF("wbnull");
		} else if (wbCount == 1) {
			d.addFV("wbfl", wb.get(0));
		} else {
			d.addFV("wbf", wb.get(0));
			for (int i=1; i<wbCount-1; i++) d.addFV("wbo", wb.get(i));
			d.addFV("wbl", wb.get(wbCount-1));
		}
		Annotation bm1f = doc.tokenEndingAt(m1span.start());
		if (bm1f != null) {
			d.addFV("bm1f", doc.text(bm1f).trim());
		}
		Annotation am2f = doc.tokenAt(m2span.end());
		if (am2f != null) {
			d.addFV("am2f", doc.text(am2f).trim());
		}

		// entity type feature
		String et12 = m1.entity.type + ":" + m2.entity.type;
		d.addFV("et12", et12);
		// -- added for subtypes --
		d.addFV("et1sub2", m1.entity.subtype + ":" + m2.entity.type);
		d.addFV("et12sub", m1.entity.type + ":" + m2.entity.subtype);
		d.addFV("et1sub2sub", m1.entity.subtype + ":" + m2.entity.subtype);

		// mention level feature
		d.addFV("ml12", m1.type + ":" + m2.type);

		// overlap features
		d.addFV("#wb", Integer.toString(wb.size()));
		String m1containsm2 = (m1span.within(m2span)) ? "true" : "false";
		String m2containsm1 = (m2span.within(m1span)) ? "true" : "false";
		d.addFV("et12+m1>m2", et12 + ":" + m1containsm2);
		d.addFV("et12+m1<m2", et12 + ":" + m2containsm1);
		d.addFV("hm12+m1>m2", et12 + ":" + m1containsm2);
		d.addFV("hm12+m1<m2", et12 + ":" + m2containsm1);

		// semantic features
		if (Ace.gazetteer.isCountry(headTokens2)) {
			d.addFV("et1_country", m1.entity.type);
		} else if (Ace.gazetteer.isNationality(headTokens2)) {
			d.addFV("et1_nationality", m1.entity.type);
		} else if (Ace.gazetteer.isCountry(headTokens1)) {
			d.addFV("country_et2", m2.entity.type);
		} else if (Ace.gazetteer.isNationality(headTokens1)) {
			d.addFV("nationality_et2", m2.entity.type);
		}
		if (isRelative(m2.headText)) {
			d.addFV("et1_relative", m1.entity.type);
		} else if (isRelative(m1.headText)) {
			d.addFV("relative_et2", m2.entity.type);
		} else {
			d.addF("not_a_relative");
		}

		// chunking features (only chunks between mentions)
		if (cpCount == 0)  {
			d.addF("chpbnull");
		} else if (cpCount == 1) {
			d.addFV("chpbfl", cp.chunks.get(0));
		} else if (cpCount > 1) {
			d.addFV("chpbf", cp.chunks.get(0));
			for (int i=1; i<cpCount-1; i++) d.addFV("chpbo", cp.chunks.get(i));
			d.addFV("chpbl", cp.chunks.get(cpCount-1));
			d.addFV("cpp", cp.toString().replace(" ","_"));
		}

		// syntactic relation (not Singapore feature)
		// head texts
		String hm1 = m1.headText.replaceAll("\\s+","_");
		String hm2 = m2.headText.replaceAll("\\s+","_");
		// entity types
		String et1 = m1.entity.type;	
		String et2 = m2.entity.type;	
		String synrel = "";
		if (syntacticRelationMap.get(m1.id + ":" + m2.id) != null)
			synrel = syntacticRelationMap.get(m1.id + ":" + m2.id) + ":";
		else if (syntacticRelationMap.get(m2.id + ":" + m1.id) != null)
			synrel = syntacticRelationMap.get(m2.id + ":" + m1.id) + "-1:";

		d.addFV ("etet", synrel + et1 + ":" + et2);
		d.addFV ("hmet", synrel + hm1 + ":" + et2);
		d.addFV ("ethm", synrel + et1 + ":" + hm2);
		d.addFV ("hmhm", synrel + hm1 + ":" + hm2);
		
		return d;
	}

	/*

	(this is an attempt at a simpler set of features;  so far, does not work as well)

	private static Datum relationFeatures (AceEntityMention m1, AceEntityMention m2) {
		Datum d = new Datum();

		// head texts
		String hm1 = m1.headText.replaceAll("\\s+","_");
		String hm2 = m2.headText.replaceAll("\\s+","_");
		// head tokens
		String[] headTokens1 = tokensIn(m1.jetHead.start(), m1.jetHead.end()).toArray(new String[0]);
		String[] headTokens2 = tokensIn(m2.jetHead.start(), m2.jetHead.end()).toArray(new String[0]);
		// entity types
		String et1 = m1.entity.type;	
		String et2 = m2.entity.type;	
		if (Ace.gazetteer.isCountry(headTokens1)) {
			et1 = "*country*";
		} else if (Ace.gazetteer.isNationality(headTokens1)) {
			et1 = "*nationality*";
		} else if (isRelative(m1.headText)) {
			et1 = "*relative*";
		}
		if (Ace.gazetteer.isCountry(headTokens2)) {
			et2 = "*country*";
		} else if (Ace.gazetteer.isNationality(headTokens2)) {
			et2 = "*nationality*";
		} else if (isRelative(m2.headText)) {
			et2 = "*relative*";
		}
		// syntactic relation
		String synrel = "";
		if (syntacticRelationMap.get(m1.id + ":" + m2.id) != null)
			synrel = syntacticRelationMap.get(m1.id + ":" + m2.id) + ":";
		else if (syntacticRelationMap.get(m2.id + ":" + m1.id) != null)
			synrel = syntacticRelationMap.get(m2.id + ":" + m1.id) + "-1:";

		d.addFV ("etet", synrel + et1 + ":" + et2);
		d.addFV ("hmet", synrel + hm1 + ":" + et2);
		d.addFV ("ethm", synrel + et1 + ":" + hm2);
		d.addFV ("hmhm", synrel + hm1 + ":" + hm2);
		
		ChunkPath cp = new ChunkPath(doc, m1, m2);
		if (cp.size() >= 0) {
			String cpstg = cp.toString().replace(" ","_");
			d.addFV ("cp", cpstg);
			d.addFV ("cpetet", cpstg + ":" + et1 + ":" + et2);
		}

		// words between mentions
		List<String> wb = tokensIn(m1.jetExtent.end(), m2.jetExtent.start());
		int wbCount = wb.size();
		d.addFV ("wbCount", Integer.toString(wbCount));

		return d;
	}

	*/

	static List<String> tokensIn (int from, int to) {
		List<String> tokens = new ArrayList<String>();
		int posn = from;
		while (posn < to) {
			Annotation tok = doc.tokenAt(posn);
			if (tok == null) break;
			tokens.add(doc.text(tok).trim());
			posn = tok.end();
		}
		return tokens;
	}

	static String[] relatives = {
		// basic
		"family",
		"parent", "parents", "father", "dad", "mother", "mom",
		"sibling", "brother", "brothers", "sister", "sisters", "sis",
		"spouse", "husband", "hubby", "wife",
		"child", "children", "baby", "daughter", "daughters", "son", "sons",
		// former
		"widow", "widower",
		// grand...
		"grandparent", "grandparents", "grandfather", "grandfathers", 
		"grandmother", "grandmothers",
		"grandchild", "grandchildren", "grandson", "grandsons", 
		"granddaughter", "granddaughters",
		// step...
		"stepparent", "stepparents", "stepfather", "stepmother", 
		"stepchild", "stepchildren", "stepson", "stepsons", 
		"stepdaughter", "stepdaughters",
		// -in-law [requires entries in dictionary]
		"father-in-law", "mother-in-law", "brother-in-law", 
		"sister-in-law", "daughter-in-law", "son-in-law",
		// other
		"cousin", "cousins", "nephew", "nephews", "uncle", "uncles", 
		"aunt", "aunts", "niece", "nieces"};

	static boolean isRelative (String s) {
		for (String r : relatives) {
			if (r.equalsIgnoreCase(s))
				return true;
		}
		return false;
	}

	/**
	 *  look for ACE relations from the answer key which have not been processed
	 *  yet -- by syntactic or adjacency patterns.
	 */

	private static void reportLeftovers () {
		for (AceRelationMention mention : relMentionList) {
			logger.warn ("Relation not used in training: {}", mention);
		}
	}

	/**
	 *  returns the pa head associated with a mention, or the text if
	 *  there is no pa
	 */

	static String getHead (AceEntityMention m) {
		Vector anns = doc.annotationsAt(m.jetHead.start(), "constit");
		if (anns != null) {
			// we search backwards so that names (which are added last)
			// will be found first
			for (int i=anns.size()-1; i>=0; i--) {
				Annotation ann = (Annotation) anns.get(i);
				String cat = (String) ann.get("cat");
				if (cat == "n" || cat == "pro" || cat == "name" ||
				    cat == "adj" || cat == "ven" ||
				    (cat == "det" && ann.get("tposs") == "t")) {
				    if (cat == "name") {
				    	String[] name = Resolve.getNameTokens(doc, ann);
				    	if (Ace.gazetteer.isCountry(name)) return "country";
				    	if (Ace.gazetteer.isNationality(name)) return "nationality";
				    }
					FeatureSet pa = (FeatureSet) ann.get("pa");
					if (pa != null) {
						String head = (String) pa.get("head");
						if (head != null) return head.replace(' ','-').replace('\n', '-');
					}
				}
			}
		}
		return doc.text(m.jetHead).trim().replace(' ','-').replace('\n', '-');
	}

	// ---- CONJUNCTION FUNCTIONS ----

	/**
	 *  finds all the conjuncts in Document doc, and builds the tables
	 *  conjunctf and conjunctb used by <CODE>getConjuncts</CODE>.
	 */

	static void findConjuncts (Document doc) {
		conjunctf.clear();
		conjunctb.clear();
		Vector constits = doc.annotationsOfType("constit");
		if (constits != null) {
			for (int j = 0; j < constits.size();  j++) {
				Annotation ann = (Annotation) constits.elementAt(j);
				Annotation conj = (Annotation) ann.get("conj");
				if (conj != null) {
					ArrayList conjuncts = new ArrayList();
					conjuncts.add(ann);
					while (conj != null) {
						conjuncts.add(conj);
						conj = (Annotation) conj.get("conj");
					}
					recordConjunct (conjuncts);
				}
			}
		}
	}

	static void recordConjunct (ArrayList conjuncts) {
		logger.trace ("recordConjuncts: {}", conjuncts);
		String type = "";
		AceEntityMention m;
		ArrayList mentions = new ArrayList();
		for (int i=0; i<conjuncts.size(); i++) {
			Annotation ann = (Annotation) conjuncts.get(i);
			m = mentionForAnnotation(ann);
			if (m == null) return;
			mentions.add(m);
			if (i == 0) {
				type = m.type;
			} else {
				if (!type.equals(m.type)) return;
			}
		}
		for (int i=0; i<mentions.size()-1; i++) {
			AceEntityMention m1 = (AceEntityMention) mentions.get(i);
			AceEntityMention m2 = (AceEntityMention) mentions.get(i+1);
			conjunctf.put(m1, m2);
			conjunctb.put(m2, m1);
			logger.trace ("Found conjuncts {} and {}",
				doc.text(m1.jetHead), doc.text(m2.jetHead));
		}
		return;
	}

	static AceEntityMention mentionForAnnotation (Annotation a) {
		Annotation argHead = Resolve.getHeadC (a);
		Span span = argHead.span();
		int start = span.start();
		return (AceEntityMention) mentionHeadMap.get(new Integer(start));
	}

	/**
	 *  returns a list of all the conjuncts of AceEntityMention <CODE>m</CODE>.
	 */

	static ArrayList getConjuncts (AceEntityMention m) {
		ArrayList a = new ArrayList();
		a.add(m);
		AceEntityMention n = m;
		while (conjunctf.get(n) != null) {
			n = (AceEntityMention) conjunctf.get(n);
			a.add(n);
			logger.trace ("Processing conjunct {} of {}", n.text, m.text);
		}
		n = m;
		while (conjunctb.get(n) != null) {
			n = (AceEntityMention) conjunctb.get(n);
			a.add(n);
			logger.trace ("Processing conjunct {} of {}", n.text, m.text);
		}
		return a;
	}

	/**
	 *  for every relation tagged by 'predictRelations' between mentions m1 and m2, see
	 *  whether m1 and m2 are conjoined;  if so, add the same relation between the
	 *  conjuncts.
	 */

	static void extendRelationsToConjuncts () {
		ArrayList<AceRelation> originalRelations = new ArrayList<AceRelation> (relationList);
		for (AceRelation originalRelation : originalRelations) {
			AceRelationMention originalMention = (AceRelationMention) originalRelation.mentions.get(0);
			AceEntityMention m1 = originalMention.arg1;
			AceEntityMention m2 = originalMention.arg2;
			// make sure mentions are disjoint
			if (m1.jetExtent.end() < m2.jetExtent.start() ||
			    m2.jetExtent.end() < m1.jetExtent.start()) {
				ArrayList a1 = getConjuncts (m1);
				ArrayList a2 = getConjuncts (m2);
				if (a1.contains(m2)) continue;
				for (int i=0; i<a1.size(); i++) {
					loop:
					for (int j=0; j<a2.size(); j++) {
						AceEntityMention c1 = (AceEntityMention) a1.get(i);
						AceEntityMention c2 = (AceEntityMention) a2.get(j);
						if (c1 == m1 && c2 == m2) continue;
						logger.trace ("Trying to add relation between {} and {}", c1.text, c2.text);
						// make sure there isn't already a relation between c1 and c2
						for (AceRelation r : relationList) {
							AceRelationMention m = (AceRelationMention) r.mentions.get(0);
							if (c1 == m.arg1 && c2 == m.arg2)
								continue loop;
						}
						// add such a relation 
						AceRelationMention mention = new AceRelationMention("", c1, c2, doc);
						String type = originalRelation.type;
						String subtype = originalRelation.subtype;
						AceRelation relation = new AceRelation("", type, subtype, "", c1.entity, c2.entity);
						relation.addMention(mention);
						relationList.add(relation);
						logger.trace ("Adding relation between {} and {}", c1.text, c2.text);
					}
				}
			}
		}
	}

	// ---- end of CONJUNCTION FUNCTIONS ----

	/**
	 *  reads the APF file from 'apfFile" and extracts the entity and relation
	 *  mentions.
	 */

	private static void readACErelations (String textFile, String apfFile) {
		aceDoc = new AceDocument(textFile, apfFile);
		findEntityMentions (aceDoc);
		findRelationMentions (aceDoc);
	}

	/**
	 *  traverses APF document (apfDOC) and
	 *     creates Mention objects and places them in mentionSet
	 *     creates MentionStartMap mapping start of head to mention
	 *     creates mentionIDMap mapping mentionID to mention
	 */

	static void findEntityMentions (AceDocument aceDoc) {
		resetMentions ();
		ArrayList entities = aceDoc.entities;
		for (int i=0; i<entities.size(); i++) {
			AceEntity entity = (AceEntity) entities.get(i);
			String type = entity.type;
			String subtype = entity.subtype;
			ArrayList mentions = entity.mentions;
			for (int j=0; j<mentions.size(); j++) {
				AceEntityMention mention = (AceEntityMention) mentions.get(j);
				addMention (mention);
			}
		}
	}

	static void resetMentions () {
		mentionHeadMap = new HashMap();
		mentionStartMap = new HashMap();
		mentionIDMap = new HashMap();
		mentionSet = new TreeSet<AceEntityMention>();
		allMentionSet = new TreeSet<AceEntityMention>();
	}

	static void addMention (AceEntityMention m) {
		// if (!m.entity.generic) 
			mentionSet.add(m);
		allMentionSet.add(m);
		mentionHeadMap.put(new Integer(m.jetHead.start()), m);
		mentionStartMap.put(new Integer(m.jetExtent.start()), m);
		mentionIDMap.put(m.id, m);
	}

	/**
	 *  puts AceRelationMentions in 'aceDoc' on relMentionList.
	 *  IMPLICIT relations (used prior to 2004) are ignored.
	 */

	private static void findRelationMentions (AceDocument aceDoc) {
		relMentionList = new ArrayList<AceRelationMention>();
		ArrayList relations = aceDoc.relations;
		for (int i=0; i<relations.size(); i++) {
			AceRelation relation = (AceRelation) relations.get(i);
			String relationClass = relation.relClass;
			if (relationClass.equals("IMPLICIT")) continue;
			relMentionList.addAll(relation.mentions);
		}
	}

	/**
	 *  if the probability of the presence of a relation > threshold,
	 *  tag the document with a relation.
	 */

	static double threshold = 0.25;

	/**
	 *  use maxent model to determine whether the pair of mentions bears some
	 *  ACE relation;  if so, add the relation to relationList.
	 */

	private static boolean predictRelation (AceEntityMention m1, AceEntityMention m2) {
		boolean local = inSameBaseNP(m1.head, m2.head);
		MaxEntModel identificationModel = local ? model0 : model2;
		MaxEntModel classificationModel = local ? model1 : model3;
		// generate features
		Datum d = relationFeatures(m1, m2);
		double p = identificationModel.prob(d, "t");
		double pnil = identificationModel.prob(d, "nil");
		String outcome = identificationModel.bestOutcome(d);
		if ((p / (p + pnil)) < threshold)
			return false;
		if (!blockingTest(m1, m2)) return false;
		if (!blockingTest(m2, m1)) return false;
		outcome = classificationModel.bestOutcome(d);
		String[] typeSubtype = outcome.split(":");
		if (typeSubtype.length != 2) {
			logger.error ("Invalid outcome in predictRelation:" + outcome);
			return false;
		}
		String type = typeSubtype[0];
		String subtype = typeSubtype[1];
		if (subtype.endsWith("-1")) {
			subtype = subtype.replace("-1","");
			if (!satisfiesTypeConstraint(type, subtype, "arg1", m2.entity.type)) return false;
			if (!satisfiesTypeConstraint(type, subtype, "arg2", m1.entity.type)) return false;
			AceRelationMention mention = new AceRelationMention("", m2, m1, doc);
			AceRelation relation = new AceRelation("", type, subtype, "", m2.entity, m1.entity);
			relation.addMention(mention);
			relationList.add(relation);
		} else {
			if (!satisfiesTypeConstraint(type, subtype, "arg1", m1.entity.type)) return false;
			if (!satisfiesTypeConstraint(type, subtype, "arg2", m2.entity.type)) return false;
			AceRelationMention mention = new AceRelationMention("", m1, m2, doc);
			AceRelation relation = new AceRelation("", type, subtype, "", m1.entity, m2.entity);
			relation.addMention(mention);
			relationList.add(relation);
		}
		return true;
	}
	
	/**
	 *  if true, multiple relation mentions (with different types) between a single pair
	 *  of entities are merged (coref'ed) into a single relation, with the type
	 *  of the first mention.
	 */

	public static boolean mergeMultipleRelations = false;

	/**
	 *  takes the set of single-mention Relations (in relationList), combines them
	 *  into multi-mention Relations, and adds them to 'aceDoc'.
	 */

	static void relationCoref (AceDocument aceDoc) {
		List<AceRelation> resolvedRelationList = new ArrayList<AceRelation>();
		logger.info ("RelationCoref: {} relation mentions", relationList.size());
	loop: for (AceRelation r : relationList) {
			AceRelationMention rm = (AceRelationMention) r.mentions.get(0);
			String eid1 = r.arg1.id;
			String eid2 = r.arg2.id;
			// search previously resolved relations
			for (AceRelation rr : resolvedRelationList) {
				if (eid1 == rr.arg1.id && eid2 == rr.arg2.id &&
				    (mergeMultipleRelations ||
				     (r.type.equals(rr.type) && r.subtype.equals(rr.subtype)))) {
					// prior relation with same type and args,
					// add latest mention as mention of prior relation
					int mentionIndex = rr.mentions.size() + 1;
					rm.id = rr.id + "-" + mentionIndex;
					rr.addMention(rm);
					continue loop;
				}
			}
			String relID = docName + "-R" + (resolvedRelationList.size() + 1);
			r.id = relID;
			rm.id = relID + "-1";
			resolvedRelationList.add(r);
			aceDoc.addRelation(r);
		}
		logger.info ("RelationCoref: {} relations", resolvedRelationList.size());
	}

	/**
	 *  if there are two mentions of the same relation in 'aceDoc' such that 
	 *  the extent of one is contained in the other, remove the mention with
	 *  larger extent.  For example, for "French citizen Smith", one can
	 *  have two relation mentions, between French and citizen and between
	 *  French and Smith.  The one between French and Smith is removed by
	 *  removeRedundantMentions.  This does not reflect any specific annotation
	 *  rule but reflects the general practice of the annotators.
	 */

	static void removeRedundantMentions (AceDocument aceDoc) {
		List<AceRelation> relations = aceDoc.relations;
		for (AceRelation relation : relations) {
			ArrayList<AceRelationMention> mentions = relation.mentions;
			Set<AceRelationMention> duplicates = new HashSet<AceRelationMention>();
			for (int i=0; i<mentions.size()-1; i++) {
				for (int j=i+1; j<mentions.size(); j++) {
					if (widerMention(mentions.get(i), mentions.get(j))) {
						duplicates.add(mentions.get(i));
					} else if (widerMention(mentions.get(j), mentions.get(i))) {
						duplicates.add(mentions.get(j));
					}
				}
			}
			if (!duplicates.isEmpty()) {
				for (AceRelationMention r : duplicates) {
					mentions.remove(r);
				}
				relation.mentions = mentions;
			}
		}
	}

	private static boolean widerMention (AceRelationMention a, AceRelationMention b) {
		int a1 = a.arg1.head.end();
		int a2 = a.arg2.head.end();
		int min_a = Math.min(a1, a2);
		int max_a = Math.max(a1, a2);
		int b1 = b.arg1.head.end();
		int b2 = b.arg2.head.end();
		int min_b = Math.min(b1, b2);
		int max_b = Math.max(b1, b2);
		return (min_a <= min_b) && (max_a >= max_b);
	}

	/**
	 *  blockingTest is an approximate implementation of the 'blocking
	 *  category rule' of Ace relation annotation.  It returns 'true' if
	 *  a relation between mentions m1 and m2 is permitted, and 'false'
	 *  if it should be blocked. <br>
	 *  If m1 is a non-head constituent of a base noun phrase, and this
	 *  base noun phrase corresponds to an Ace entity, then m2 must
	 *  be a constituent (head or non-head) of the same noun group.
	 */

	static boolean blockingTest (AceEntityMention m1, AceEntityMention m2) {
		Span span1 = m1.jetHead;
		Span span2 = m2.jetHead;
		Annotation baseNP = containingBaseNP (span1);
		if (baseNP == null)
			return true;
		Span npSpan = baseNP.span();
		// if m1 is the head of the noun group, return true
		// (if m1 has a right conjunct, it might be one of a set of
		//  conjoined heads;  we return true if in doubt)
		if (span1.end() == npSpan.end())
			return true;
		if (conjunctf.get(m1) != null)
			return true;
		// if the noun group does not correspond to an Ace
		// entity, return true
		if (mentionStartMap.get(npSpan.start()) == null)
			return true; 
		// otherwise return true only if m2 is also in noun group
		boolean v = span2.within(npSpan);
		if (!v)
			logger.trace ("Relation blocking test fails for {} - {}", m1.text, m2.text);
		return v;
	}

	/**
	 *  if Span 's' is contained within a base NP, return that base NP, else return null.
	 */

	static Annotation containingBaseNP (Span s) {
		int pos = s.start();
		Annotation np = null;
		loop: while (true) {
			Vector<Annotation> constits = doc.annotationsAt(pos, "constit");
			if (constits != null)
				for (Annotation a : constits)
					if (isBaseNp(a)) {
						np = a;
						break loop;
					}
			Annotation t = doc.tokenEndingAt(pos);
			if (t == null) 
				return null;
			pos = t.start();
		}
		if (np.end() >= s.end())
			return np;
		return null;
	}

	static boolean isBaseNp (Annotation a) {
		if (a.get("cat") != "np")
			return false;
		Annotation h = (Annotation) a.get("headC");
		return h != null && h.get("cat") != "np";
	}

	/**
	 *  returns 'true' if Spans 's1' and 's2' are contained within the same base NP
	 */

	static boolean inSameBaseNP (Span s1, Span s2) {
		Annotation np = containingBaseNP(s1);
		if (np == null) return false;
		return s2.within(np.span());
	}

}
// to do:
// IMPLICIT relations
// generic entities
