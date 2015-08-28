// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2006
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.JetTest;
import Jet.Tipster.*;
import Jet.Lex.*;
import Jet.Scorer.*;
import Jet.Lisp.*;
import Jet.Zoner.SentenceSplitter;
import Jet.Console;
import AceJet.Gazetteer;
import Jet.Control;
import Jet.Refres.Resolve;
import Jet.Pat.Pat;
import Jet.MaxEntModel;
import AceJet.*;

/**
 *  A modified version of HMMNameTagger for experiments with N-best tagging.
 *  This skeleton does rescoring using a combination of
 *  - the probability from the baseline HMM
 *  - a coref measure (the total number of name coreferences)
 *  - a relation measure (the total number of relations)
 */

public class XNameTagger {

	public HMM nameHMM;
	public HMMannotator annotator;
	String[][] tagTable;
	String[] NEtypeTable;
	String[] tagsToRead;
	String[] tagsToCache;
	String[] tagsToScore;
	Class emitterClass;

	/**
	 *  the N for N-best.
	 */
	int N = 20;
	/**
	 *  the max ent model to score NE hypotheses
	 */
	MaxEntModel model = new MaxEntModel("rerankTemp/features.log",
	                                    "rerankTemp/model.log");

	/**
	 *  creates a new XNameTagger (with an empty HMM).
	 *  @param emitterClass  the class of the emitter associated with each state of
	 *                       the HMM;  must be a subclass of <CODE>HMMemitter</CODE>.
	 */

	public XNameTagger (Class emitterClass) {
		if (!HMMemitter.class.isAssignableFrom(emitterClass)) {
			System.out.println ("XNameTagger constructor invoked with invalid class " + emitterClass);
			return;
		}
		this.emitterClass = emitterClass;
		nameHMM = new HMM(emitterClass);
		annotator = new HMMannotator(nameHMM);
		annotator.setBItag (false);
		annotator.setAnnotateEachToken (false);
		nameHMM.setNbest();
	}

	/**
	 *  read the list of annotation types and features from file 'tagFileName'.
	 */

	private void readTagTable (String tagFileName) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(tagFileName));
      readTagTable (in);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

	/**
	 *  read the list of annotation types and features from BufferedReader 'in'.
	 *  Each line must be of the form <br>
	 *  annotationType HMMtag <br>
	 *  or
	 *  annotationType feature featureValue HMMtag <br>
	 *  where 'HMMtag' ties this line to a state of the HMM.
	 */

  private void readTagTable (BufferedReader in) {
  	annotator.readTagTable(in);
  	HashSet annotationTypes = new HashSet();
    ArrayList hmmTagList = new ArrayList();
		String[][] tagTable = annotator.getTagTable();
		for (int i=0; i<tagTable.length; i++) {
			annotationTypes.add(tagTable[i][0]);
		  hmmTagList.add(tagTable[i][3]);
		}
    NEtypeTable = (String[]) hmmTagList.toArray(new String[0]);
    tagsToCache = NEtypeTable;
    //  no name cache in baseline system
    // nameHMM.setTagsToCache (tagsToCache);
    tagsToScore = (String[]) annotationTypes.toArray(new String[0]);
    annotationTypes.add("SENT");
    annotationTypes.add("TURN");
    tagsToRead = (String[]) annotationTypes.toArray(new String[0]);
  }

  /**
   *  load an HMM from file <CODE>fileName</CODE>.
   */

	public void load (String fileName) throws IOException {
		BufferedReader in = new BufferedReader
			(new InputStreamReader
				(new FileInputStream(fileName), JetTest.encoding));
		readTagTable(in);
		nameHMM.load(in);
	}

	/**
	 *  tag Document 'doc' for NE's, assigning N-best alternative tag
	 *  assignments to each sentence.
	 */

	private void tagDocument (Document doc) {
		doc.annotateWithTag ("TEXT");
		Vector<Annotation> textSegments = doc.annotationsOfType ("TEXT");
		for (Annotation ann : textSegments) {
		    Span textSpan = ann.span ();
				Ace.monocase = Ace.allLowerCase(doc);
		    SentenceSplitter.split (doc, textSpan);
		}
		Vector<Annotation> sentences = doc.annotationsOfType ("sentence");
		int isent = 0;
		for (Annotation sentence : sentences) {
			Span sentenceSpan = sentence.span();
			Tokenizer.tokenize (doc, sentenceSpan);
			tag (doc, sentenceSpan, "H" + isent);
			isent++;
		}
	}

	/**
	 *  tag span 'span' of Document 'doc' with N-best Named Entity annotations.
	 *
	 *  @return a list of the hypothesis id-s
	 */

	public ArrayList tag (Document doc, Span span, String sentno) {
		return annotator.annotateSpanNbest (doc, span, N, sentno);
	}

	/**
	 *  computes the recall/precision of 'testCollection' with respect to
	 *  'keyCollection' (which should have the same documents) with respect
	 *  to the name annotations in 'tagsToScore'.  Reports both per-document
	 *  and total scores to System.out.
	 */

	public void scoreCollection (String testCollection, String keyCollection) {
		DocumentCollection testCol = new DocumentCollection(testCollection);
		testCol.open();
		DocumentCollection keyCol = new DocumentCollection(keyCollection);
		keyCol.open();
		if (testCol.size() != keyCol.size()) {
			System.out.println (" ** Test and key collections have different sizes, cannot evaluate.");
			return;
		}
		int tagsInResponses = 0;
		int tagsInKeys = 0;
		int matchingTags = 0;
		int matchingAttrs = 0;
		for (int i=0; i<testCol.size(); i++) {
			// if (i != 8) continue; // <<< debug
			// open test document
			ExternalDocument testDoc = testCol.get(i);
			// testDoc.setSGMLtags (tagsToRead);
			testDoc.setAllTags (true);
			testDoc.open();
			nameHMM.newDocument();
			// annotate test document
			System.out.println ("Annotating document " + i + ": " + testDoc.fileName());
			Set bestHypotheses = NbestTagDocument (testDoc);
			testDoc.setActiveHypotheses(bestHypotheses);
			// display with 'view'
			// if (i < 60) new View (testDoc, i);
			// open key document and display it
			ExternalDocument keyDoc = keyCol.get(i);
			// keyDoc.setSGMLtags (tagsToRead);
			keyDoc.setAllTags (true);
			keyDoc.open();
			keyDoc.stretch("ENAMEX");
			Vector textSpans = keyDoc.annotationsOfType("TEXT");
			if (textSpans == null) {
				System.out.println ("No <TEXT> in " + testDoc.fileName() + ", cannot be scored.");
				continue;
			}
			// if (i < 60) new View (keyDoc, i + 100);
			// score name tags
			SGMLScorer scorer = new SGMLScorer(testDoc, keyDoc);
			for (int itag = 0; itag<tagsToScore.length; itag++) {
				scorer.match(tagsToScore[itag]);
				Console.println (scorer.report());
			}
			System.out.println ("Tags in response:        " + scorer.totalTagsInDoc1);
			System.out.println ("Tags in key:             " + scorer.totalTagsInDoc2);
			System.out.println ("Matching tags:           " + scorer.totalMatchingTags);
			System.out.println ("Matching attributes:     " + scorer.totalMatchingAttrs);
			System.out.println ("Type recall:             " +
			                    (float) scorer.totalMatchingTags / scorer.totalTagsInDoc2);
			System.out.println ("Type precision:          " +
			                    (float) scorer.totalMatchingTags / scorer.totalTagsInDoc1);
			System.out.println ("Attribute recall:        " +
			                    (float) scorer.totalMatchingAttrs / scorer.totalTagsInDoc2);
			System.out.println ("Attribute precision:     " +
			                    (float) scorer.totalMatchingAttrs / scorer.totalTagsInDoc1);
			tagsInResponses += scorer.totalTagsInDoc1;
			tagsInKeys      += scorer.totalTagsInDoc2;
			matchingTags    += scorer.totalMatchingTags;
			matchingAttrs   += scorer.totalMatchingAttrs;
		}
		System.out.println ("\nTotal counts:");
		System.out.println ("      matchingTags =    " + matchingTags);
		System.out.println ("      matchingAttrs =   " + matchingAttrs);
		System.out.println ("      tagsInKeys =      " + tagsInKeys);
		System.out.println ("      tagsInResponses = " + tagsInResponses);
		System.out.println ("Overall Type Recall:          " +
		                    (float) matchingTags / tagsInKeys);
		System.out.println ("Overall Type Precision:       " +
		                    (float) matchingTags / tagsInResponses);
		System.out.println ("Overall Attribute Recall:     " +
		                    (float) matchingAttrs / tagsInKeys);
		System.out.println ("Overall Attribute Precision:  " +
		                    (float) matchingAttrs / tagsInResponses);
	}

	/**
	 *  computes the recall/precision of 'testCollection' with respect to
	 *  'keyCollection' (which should have the same documents) with respect
	 *  to the name annotations in 'tagsToScore'.  Reports both per-document
	 *  and total scores to System.out.
	 *      This modified version computes both the score for the first
	 *  hypothesis (the baseline score) and the score for the best of the N
	 *  hypotheses (the ceiling for N-best rescoring), where 'best' is defined
	 *  in terms of hypothesis overlap.
	 */

	public void scoreCollectionWithOracle (String testCollection, String keyCollection) {
		DocumentCollection testCol = new DocumentCollection(testCollection);
		testCol.open();
		DocumentCollection keyCol = new DocumentCollection(keyCollection);
		keyCol.open();
		if (testCol.size() != keyCol.size()) {
			System.out.println (" ** Test and key collections have different sizes, cannot evaluate.");
			return;
		}
		int tagsInResponses = 0;
		int tagsInBestResponses = 0;
		int tagsInKeys = 0;
		int matchingTags = 0;
		int matchingAttrs = 0;
		int matchingAttrsNbest = 0;
		for (int i=0; i<testCol.size(); i++) {
			// open test document
			ExternalDocument testDoc = testCol.get(i);
			testDoc.setAllTags (true);
			testDoc.open();
			testDoc.stretchAll();
			nameHMM.newDocument();
			// annotate test document
			System.out.println ("Annotating document " + i + ": " + testDoc.fileName());
			tagDocument (testDoc);
			// display with 'view'
			if (i < 3) new View (testDoc, i);
			// open key document and display it
			ExternalDocument keyDoc = keyCol.get(i);
			keyDoc.setAllTags (true);
			keyDoc.open();
			keyDoc.stretch("ENAMEX");
			Vector textSpans = keyDoc.annotationsOfType("TEXT");
			if (textSpans == null) {
				System.out.println ("No <TEXT> in " + testDoc.fileName() + ", cannot be scored.");
				continue;
			}
			if (i < 3) new View (keyDoc, i+100);
			// score name tags
			HashSet hypoSet = new HashSet();
			int totalTagsInDoc1 = 0;
			int totalTagsInDoc2 = 0;
			int totalMatchingAttrs = 0;
			int totalTagsInDoc1Nbest = 0;
			int totalMatchingAttrsNbest = 0;
			SGMLScorer scorer = new SGMLScorer(testDoc, keyDoc);
			for (int itag = 0; itag<tagsToScore.length; itag++) {
				// System.out.println ("For tag " + tagsToScore[itag]);  // <<<
				Vector<Annotation> sentences = testDoc.annotationsOfType ("sentence");
				if (sentences == null) continue;
				for (int isent=0; isent<sentences.size(); isent++) {
					// System.out.println ("For sentence " + isent); // <<<
					Annotation sentence = sentences.get(isent);
					Span sentenceSpan = sentence.span();
					int bestMatchingAttrs = -1;
					int bestTagsInDoc1 = 0;
					for (int ihyp = 0; ihyp<N; ihyp++) {
						// System.out.println ("For hypothesis " + ihyp); // <<<
						String hypo = "H" + isent + "-" + ihyp;
						hypoSet.clear();
						hypoSet.add(hypo);
						testDoc.setActiveHypotheses(hypoSet);
						scorer.match(tagsToScore[itag], tagsToScore[itag], sentenceSpan);
						testDoc.setActiveHypotheses(null);
						// System.out.println ("numOfTagsInDoc1 = " + scorer.numOfTagsInDoc1);
						// System.out.println ("numOfTagsInDoc2 = " + scorer.numOfTagsInDoc2);
						// System.out.println ("numOfMatchingAttrs = " + scorer.numOfMatchingAttrs);
						if (ihyp == 0) {
							totalTagsInDoc1 += scorer.numOfTagsInDoc1;
							totalTagsInDoc2 += scorer.numOfTagsInDoc2;
							totalMatchingAttrs += scorer.numOfMatchingAttrs;
						}
						if (scorer.numOfMatchingAttrs > bestMatchingAttrs) {
							bestTagsInDoc1 = scorer.numOfTagsInDoc1;
							bestMatchingAttrs = scorer.numOfMatchingAttrs;
						}
					}
					totalTagsInDoc1Nbest += bestTagsInDoc1;
					totalMatchingAttrsNbest += bestMatchingAttrs;
				}
			}
			System.out.println ("Total tags in baseline response:    " + totalTagsInDoc1);
			System.out.println ("Total tags in best of N response:   " + totalTagsInDoc1Nbest);
			System.out.println ("Total tags in key:                  " + totalTagsInDoc2);
			System.out.println ("Matching attributes (baseline):     " + totalMatchingAttrs);
			System.out.println ("Matching attributes (best of N):    " + totalMatchingAttrsNbest);
			System.out.println ("Attribute recall (baseline):        " +
			                    (float) totalMatchingAttrs / totalTagsInDoc2);
			System.out.println ("Attribute precision (baseline):     " +
			                    (float) totalMatchingAttrs / totalTagsInDoc1);
			System.out.println ("Attribute recall (best of N):       " +
			                    (float) totalMatchingAttrsNbest / totalTagsInDoc2);
			System.out.println ("Attribute precision (best of N):    " +
			                    (float) totalMatchingAttrsNbest / totalTagsInDoc1Nbest);
			tagsInResponses += totalTagsInDoc1;
			tagsInBestResponses += totalTagsInDoc1Nbest;
			tagsInKeys      += totalTagsInDoc2;
			matchingAttrs   += totalMatchingAttrs;
			matchingAttrsNbest  += totalMatchingAttrsNbest;
		}
		System.out.println ("\nTotal baseline counts:");
		System.out.println ("      matchingAttrs =   " + matchingAttrs);
		System.out.println ("      tagsInKeys =      " + tagsInKeys);
		System.out.println ("      tagsInResponses = " + tagsInResponses);
		System.out.println ("Overall Baseline Attribute Recall:           " +
		                    (float) matchingAttrs / tagsInKeys);
		System.out.println ("Overall Baseline Attribute Precision:        " +
		                    (float) matchingAttrs / tagsInResponses);
		System.out.println ("Overall Best of N Attribute Recall:          " +
		                    (float) matchingAttrsNbest / tagsInKeys);
		System.out.println ("Overall Best of N Attribute Precision:       " +
		                    (float) matchingAttrsNbest / tagsInBestResponses);
	}

	final static String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
	final static boolean useAceBigrams = false;
	static final String oldACEdir =
	    home + "ACE/";
	static final String ACEdir =
	    home + "ACE 05/V4/";
	static final String dictFile = ACEdir + "EDT type dict.txt";
	static final String genericFile = ACEdir + "generic dict.txt";
	static final String patternFile = oldACEdir + "relations/" + "patterns04.log";


	/**
	 *  procedures for training and testing the named entity tagger with rescoring.
	 *  At present it is set up to train and test on the same documents, a
	 *  20-document set of ACE 2004 documents.
	 */

	public static void main (String[] args) throws IOException {
		JetTest.initializeFromConfig("props/Nbest.properties");
  	Ace.gazetteer = new Gazetteer();
		Ace.gazetteer.load("data/loc.dict");
		EDTtype.readTypeDict(dictFile);
		EDTtype.readGenericDict (genericFile);
		Ace.setPatternSet (patternFile);

  	Pat.trace = false;
  	Resolve.trace = false;
  	Resolve.ACE = true;
  	Ace.entityTrace = false;
  	LearnRelations.relationTrace = false;
  	Resolve.nameTypeMatch = true;

		// to train N-best model
		// NbestTrain ();
		// to score N-best model
		NbestScore ();
	}

	private static void NbestScore () throws IOException {
		XNameTagger nt = new XNameTagger(WordFeatureHMMemitter.class);
		nt.annotator.setRecordProb(true);
		nt.load ("acedata/ACEname04HMM.txt");
		nt.model.loadModel();
		String testCollection = home + "ACE/training04 nwire 20 sgm.txt";
		String keyCollection = home + "ACE/training04 nwire 20 ne.txt";
		BigramHMMemitter.useBigrams = false;
		System.out.println ("\nBaseline / upper bound\n");
		nt.scoreCollectionWithOracle (testCollection, keyCollection);
		System.out.println ("\nN-Best\n");
		nt.scoreCollection (testCollection, keyCollection);
	}

//============== t r a i n i n g

	/**
	 *  generate training data for training of a reranking model using
	 *  coreference features to rerank NE hypotheses.  Generate data from
	 *  20 2004 ACE documents.
	 */

  private static void NbestTrain () throws IOException {
		String testCollection = home + "ACE/training04 nwire 20 sgm.txt";
		String keyCollection = home + "ACE/training04 nwire 20 ne.txt";
		XNameTagger nt = new XNameTagger(WordFeatureHMMemitter.class);
		nt.annotator.setRecordProb(true);
		nt.load ("acedata/ACEname04HMM.txt");
		BigramHMMemitter.useBigrams = false;
		nt.NbestTrainCollection (testCollection, keyCollection);
	}

  /**
   *  generate reranking model training data from documents in 'testCollection',
   *  where corresponding NE keys are in 'keyCollection'.
   */

  public void NbestTrainCollection (String testCollection, String keyCollection)
  		throws IOException {
		DocumentCollection testCol = new DocumentCollection(testCollection);
		testCol.open();
		DocumentCollection keyCol = new DocumentCollection(keyCollection);
		keyCol.open();
		for (int i=0; i<testCol.size(); i++) {
			// if (i > 3) continue; // just try 4 documents
			// open test document
			ExternalDocument testDoc = testCol.get(i);
			testDoc.setSGMLtags (tagsToRead);
			testDoc.open();
			nameHMM.newDocument();
			// open key document
			ExternalDocument keyDoc = keyCol.get(i);
			keyDoc.setSGMLtags (tagsToRead);
			keyDoc.open();
			keyDoc.stretch("ENAMEX");
			// annotate test document
			System.out.println ("Annotating document " + i + ": " + testDoc.fileName());
			NbestTrainDocument (testDoc, keyDoc);
		}
		model.buildModel();
		model.saveModel();
	}

	// HashTables to hold information about each (sentence) hypothesis
	// the F-score of the hypothesis
	HashMap<String, Double> Fscore;
	// the number of coreferring names in the hypothesis
	HashMap<String, Integer> corefMentionCount;
	// the number of relations in the hypothesis
	HashMap<String, Integer> relationCount;
	// the (baseline) (log) probability of the HMM
	HashMap<String, Integer> NEprob;
	// ------------------ debug
	// the hypothesis itself, with XML NE markup
	HashMap<String, StringBuffer> NEstring;
	// the relations for this hypothesis
	HashMap<String, String> relString;
	// ------------------ debug

	/**
	 *  generate training data from document 'doc', where corresponding NE key
	 *  is in 'keyDoc', and write training data to 'NEfeatureWriter'.
	 */

	private void NbestTrainDocument (Document doc, Document keyDoc) {
		// clear tables with info about each hypothesis
		Fscore = new HashMap();
		corefMentionCount = new HashMap();
		relationCount = new HashMap();
		// ------------------ debug
		NEstring = new HashMap();
		relString = new HashMap();
		// ------------------ debug
		NEprob = new HashMap();
		SGMLScorer scorer = new SGMLScorer(doc, keyDoc);
		doc.annotateWithTag ("TEXT");
		Vector textSegments = doc.annotationsOfType ("TEXT");
    Iterator it = textSegments.iterator ();
    while (it.hasNext ()) {
        Annotation ann = (Annotation)it.next ();
        Span textSpan = ann.span ();
        // check document case
				Ace.monocase = Ace.allLowerCase(doc);
        SentenceSplitter.split (doc, textSpan);
    }
    Vector sentences = doc.annotationsOfType ("sentence");
    // for each sentence, store list of hypothesis names
    ArrayList[] hypotheses = new ArrayList[sentences.size()];
    // process individual sentences (NE tag, chunk, patterns, ...)
    // build for each hypothesis
    //    Fscore(hypothesis) = NE F score of hypothesis
    //    corefMentionCount(hypothesis)
    //    NEprob(hypothesis)
    // these are stored as 3 hash tables, indexed by hypothesis
  	NEchunkNbest (doc, sentences, hypotheses, true, scorer);
    doc.setActiveHypotheses(null);
    new View (doc, 0);
    for (int isent=0; isent < sentences.size(); isent++) {
    	System.out.println ("### Analyzing hypotheses for sentence " + isent);
    	refresNbest (doc, isent, sentences, hypotheses, corefMentionCount);
    	relationNbest (doc, isent, sentences, hypotheses, relationCount);
    	ArrayList sentenceHypotheses = hypotheses[isent];
			// compute maximum F score
			String hypo0 = (String) sentenceHypotheses.get(0);
			double bestFscore = -1.0;
			for (int ihypo=0; ihypo < sentenceHypotheses.size(); ihypo++) {
				String hypoi = (String) sentenceHypotheses.get(ihypo);
				Double Fscorei = (Double) Fscore.get(hypoi);
				bestFscore = Math.max (bestFscore, Fscorei.doubleValue());
			}
			for (int ihypo=0; ihypo < sentenceHypotheses.size(); ihypo++) {
				// build feature vector for each hypothesis
				String hypoi = (String) sentenceHypotheses.get(ihypo);
				Datum d = rescoringFeatures(hypo0, hypoi);
				// outcome:  best NE F score (within 1%)
				Double Fscorei = (Double) Fscore.get(hypoi);
				boolean best = Fscorei.doubleValue() + 0.01 > bestFscore;
				d.setOutcome (best ? "best" : "notBest");
				model.addEvent(d);
			}
		}
		doc.setActiveHypotheses(null);
	}

	/**
	 *  returns a Datum (feature set) with the features to be used for rescoring:
	 *  - the relative NE (log) probability
	 *  - the coref count, relative to the baseline
	 *  - the relation count, relative to the baseline
	 */

	private Datum rescoringFeatures (String hypo0, String hypoi) {
		Datum d = new Datum();
		int bestNEprob = NEprob.get(hypo0).intValue();
		int baseCorefScore = corefMentionCount.get(hypo0).intValue();
		int baseRelationScore = relationCount.get(hypo0).intValue();
		// = NE prob, relative to baseline (best prob.)
		Integer NEprobi = NEprob.get(hypoi);
		int relativeNEprob = NEprobi.intValue() - bestNEprob;
		d.addFV ("NEprob", Integer.toString(relativeNEprob));
		// = corefScore, relative to baseline
		Integer corefScorei = corefMentionCount.get(hypoi);
		int relativeCorefScore = corefScorei.intValue() - baseCorefScore;
		d.addFV ("coref", Integer.toString(relativeCorefScore));
		// = relationCount, relative to baseline
		Integer relationScorei = relationCount.get(hypoi);
		int relativeRelationScore = relationScorei.intValue() - baseRelationScore;
		// ---------------------------  debug
		if (relativeRelationScore > 0) {
			System.out.println ("reln = " + relativeRelationScore + " ------------");
			System.out.println ("base: " + NEstring.get(hypo0));
			System.out.println ("base: " + relString.get(hypo0));
			System.out.println ("hypo: " + NEstring.get(hypoi));
			System.out.println ("hypo: " + relString.get(hypoi));
		}
		// ---------------------------  debug
		d.addFV ("reln", Integer.toString(relativeRelationScore));
		return d;
	}

// ========================== t a g g i n g


	int MARGIN_TO_SKIP_RESCORING = 3;

	/**
	 *  tag Document 'doc' (for NE, chunk, ... ) using an N-best reranking
	 *  strategy.  Returns the set of hypotheses (one per sentence) of the
	 *  best ranked analysis.
	 */

	private Set<String> NbestTagDocument (Document doc) {
		// clear tables with info about each hypothesis
		corefMentionCount = new HashMap<String, Integer>();
		relationCount = new HashMap<String, Integer>();
		NEprob = new HashMap();
		// ------------------ debug
		NEstring = new HashMap();
		relString = new HashMap();
		// ------------------ debug
		// set of best hypotheses after reranking
		Set<String> bestHypotheses = new HashSet<String>();
		doc.annotateWithTag ("TEXT");
		Vector<Annotation> textSegments = doc.annotationsOfType ("TEXT");
    for(Annotation ann : textSegments) {
        Span textSpan = ann.span ();
				Ace.monocase = Ace.allLowerCase(doc);
        SentenceSplitter.split (doc, textSpan);
    }
    Vector sentences = doc.annotationsOfType ("sentence");
    // for each sentence, store list of hypothesis names
    ArrayList[] hypotheses = new ArrayList[sentences.size()];
    // process individual sentences (NE tag, chunk, patterns, ...),
    // generating alternative hypotheses for each sentence
  	NEchunkNbest (doc, sentences, hypotheses, false, null);
    for (int isent=0; isent < sentences.size(); isent++) {
    	System.out.println ("### Analyzing hypotheses for sentence " + isent);
    	ArrayList sentenceHypotheses = hypotheses[isent];
			String hypo0 = (String) sentenceHypotheses.get(0);
			// skip rescoring if only one hypothesis
			if (sentenceHypotheses.size() == 1) {
				System.out.println ("    Only one hypothesis.");
				bestHypotheses.add(hypo0);
				continue;
			}
			// skip rescoring if large margin
			String hypo1 = (String) sentenceHypotheses.get(1);
			int NEprob0 = NEprob.get(hypo0).intValue();
			int NEprob1 = NEprob.get(hypo1).intValue();
			int margin = NEprob0 - NEprob1;
			if (margin > MARGIN_TO_SKIP_RESCORING) {
				System.out.println ("    Margin = " + margin + " (no rescoring)");
				bestHypotheses.add(hypo0);
				continue;
			}
			// do coreference and relation tagging
    	refresNbest (doc, isent, sentences, hypotheses, corefMentionCount);
    	relationNbest (doc, isent, sentences, hypotheses, relationCount);
    	// select hypothesis with highest probability of being 'best'
			String bestHypo = hypo0;
			double bestScore = 0;
			for (int ihypo=0; ihypo < sentenceHypotheses.size(); ihypo++) {
				String hypoi = (String) sentenceHypotheses.get(ihypo);
				Datum d = rescoringFeatures (hypo0, hypoi);
				double score = model.prob(d, "best");
				System.out.println ("    Hypothesis " + hypoi + " score " + score);
				if (score > bestScore) {
						bestScore = score;
						bestHypo = hypoi;
				}
			}
			bestHypotheses.add(bestHypo);
			if (!bestHypo.equals(hypo0))
				System.out.println ("    Picked hypothesis " + bestHypo);
		}
		doc.setActiveHypotheses(null);
		return bestHypotheses;
	}

	/**
	 *  NE hypotheses whose (log) prob is more than NE_PROBABILITY_WINDOW below
	 *  the max (log) prob are ignored.
	 */

	static int NE_PROBABILITY_WINDOW = 6;

	/**
	 *  perform Nbest name tagging and other sentence-by-sentence processing
	 *  for document 'doc'.  Stores the hypotheses for sentence i in hypotheses[i],
	 *  and records the NE probability and (if training) the F score of each
	 *  hypothesis.
	 */

	private void NEchunkNbest (Document doc, Vector sentences,
	    ArrayList[] hypotheses, boolean training, SGMLScorer scorer) {
		HashSet activeHypotheses = new HashSet();
		for (int isent=0; isent < sentences.size(); isent++) {
    	Annotation sentence = (Annotation)sentences.get(isent);
    	Span sentenceSpan = sentence.span();
    	// tokenize sentence
    	Tokenizer.tokenize (doc, sentenceSpan);
    	// generate Nbest NE tagging hypotheses
    	ArrayList allSentenceHypotheses = tag (doc, sentenceSpan, "H" + isent);
    	ArrayList sentenceHypotheses = new ArrayList();
    	int prob0 = 0;
    	for (int ihypo=0; ihypo < allSentenceHypotheses.size(); ihypo++) {
    		String hypothesis = (String) allSentenceHypotheses.get(ihypo);
    		doc.setCurrentHypothesis (hypothesis);
    		activeHypotheses.clear();
    		activeHypotheses.add(hypothesis);
    		doc.setActiveHypotheses (activeHypotheses);
    		// retrieve log probability from HMM tagger for this hypothesis,
    		// which is recorded as feature 'prob' of tag 'HMMtags' by HMMannotator
    		Vector NEs = doc.annotationsAt(sentenceSpan.start(), "HMMtags");
    		Annotation NE = (Annotation) NEs.get(0);
    		Integer prob = (Integer) NE.get("prob");
    		if (ihypo == 0)
    			prob0 = prob.intValue();
    		else
    			if (prob.intValue() <= prob0 - NE_PROBABILITY_WINDOW) break;
    		sentenceHypotheses.add(hypothesis);
    		NEprob.put(hypothesis, prob);
    		// for training, get F score of hypothesis, compared to key
    		if (training)
    			Fscore.put(hypothesis, scoreSentence(scorer, sentenceSpan));
    		// do rest of processing for this hypothesis (lexical lookup,
    		// chunking, etc.)
    		Control.processSentence(doc, sentenceSpan);
    	}
    	// store ArrayList of hypotheses for sentence i as hypotheses[i]
    	hypotheses[isent] = sentenceHypotheses;
    }
  }

  /**
	 *  returns the NE F score for the sentence.
	 */

	static Double scoreSentence (SGMLScorer scorer, Span span) {
		scorer.match("ENAMEX", "ENAMEX", span);
		double recall = (scorer.numOfTagsInDoc2 == 0) ? 1. :
		                (double) scorer.numOfMatchingAttrs / scorer.numOfTagsInDoc2;
		double precision = (scorer.numOfTagsInDoc1 == 0) ? 1. :
		                (double) scorer.numOfMatchingAttrs / scorer.numOfTagsInDoc1;
		double f = 2. / ( (1. / recall) + (1. / precision) );
		return new Double(f);
	}

	/**
	 *  perform reference resolution to compare all the hypotheses for sentence
	 * 'isent' of the document.   Resolution is performed using the first
	 *  hypothesis for all other sentences.  The coref count for each hypothesis
	 *  is saved in corefMentionCount, indexed by the hypothesis.
	 *
	 *  @param  doc        the document being processed
	 *  @param  isent      the number of the sentence being processed
	 *  @param  sentences  the sentences of the document
	 *  @param  hypotheses an array with one element for each sentence,
	 *                     a list of the hypotheses for that sentence
	 *  @param  corefMentionCount a map from hypotheses to the coreference
	 *                     count for that hypothesis
	 */

	private void refresNbest (Document doc, int isent, Vector<Annotation> sentences,
				ArrayList[] hypotheses, HashMap<String, Integer> corefMentionCount) {
   	Annotation sentence = sentences.get(isent);
   	ArrayList sentenceHypotheses = hypotheses[isent];
		HashSet activeHypotheses = new HashSet();
		for (int ihypo=0; ihypo < sentenceHypotheses.size(); ihypo++) {
			String hypo = (String) sentenceHypotheses.get(ihypo);
			System.out.println ("### coref for hypothesis " + hypo);
			// set activeHypotheses = ihypo for isent, first for all other sentences
			activeHypotheses.clear();
			for (int jsent=0; jsent < sentences.size(); jsent++) {
				if (jsent == isent)
					activeHypotheses.add(hypo);
				else
					activeHypotheses.add(hypotheses[jsent].get(0));
			}
			doc.setActiveHypotheses (activeHypotheses);
			doc.setCurrentHypothesis (null);
			//  do refres for entire document
			for (int jsent=0; jsent < sentences.size(); jsent++) {
				Annotation jsentence = (Annotation)sentences.get(jsent);
		  	Span sentenceSpan = jsentence.span();
		  	Resolve.references (doc, sentenceSpan);
			}
			//  get coref score for hypothesis
			corefMentionCount.put(hypo, new Integer(corefCount(doc, sentence.span())));
			doc.removeAnnotationsOfType("entity");
		}
	}

	/**
	 *  returns the count of the (number of mentions - 2) for all the entities which
	 *  include (as head) one of the NEs in span 'sentence'.
	 */

	static int corefCount (Document doc, Span sentence) {
		Vector<Annotation> entities = doc.annotationsOfType("entity");
		int mentionCount = 0;
		for (Annotation entity : entities) {
			Vector mentions = (Vector) entity.get("mentions");
			boolean relevantEntity = false;
			for (int imention=0; imention<mentions.size(); imention++) {
				Annotation mention = (Annotation) mentions.get(imention);
				Annotation head = Resolve.getHeadC(mention);
				if (mention.span().within(sentence) &&
				    head.get("cat") == "name" /* mention is a named mention */) {
					relevantEntity = true;
					break;
				}
			}
			if (relevantEntity)
				mentionCount += Math.min((mentions.size() - 2), 1);
		}
		return mentionCount;
	}

	/**
	 *  perform Ace relation tagging on all the hypotheses for sentence 'isent',
	 *  and recordd in 'relationCount' the number of relations (in this
	 *  sentence) for each hypothesis
	 *
	 *  @param  doc        the document being processed
	 *  @param  isent      the number of the sentence being processed
	 *  @param  sentences  the sentences of the document
	 *  @param  hypotheses an array with one element for each sentence,
	 *                     a list of the hypotheses for that sentence
	 *  @param  relationCount a map from hypotheses to the relation
	 *                     count for that hypothesis
	 */

	private void relationNbest (Document doc, int isent, Vector<Annotation> sentences,
				ArrayList[] hypotheses, HashMap<String, Integer> relationCount) {
		// activate just one hypothesis
		Annotation sentence = sentences.get(isent);
		Span sentenceSpan = sentence.span();
   	ArrayList sentenceHypotheses = hypotheses[isent];
		HashSet activeHypotheses = new HashSet();
		for (int ihypo=0; ihypo < sentenceHypotheses.size(); ihypo++) {
			String hypo = (String) sentenceHypotheses.get(ihypo);
			System.out.println ("### relations for hypothesis " + hypo);
			activeHypotheses.clear();
			activeHypotheses.add(hypo);
			doc.setActiveHypotheses (activeHypotheses);
			doc.setCurrentHypothesis (null);
		// run refres (resolving only within the single sentence)
			Resolve.references (doc, sentenceSpan);
		// create dummy ace doc
			AceDocument aceDoc = new AceDocument (null, null, "X", doc.text());
		// create Ace entities
			Ace.buildAceEntities (doc, "X", aceDoc);
		// create relations
			LearnRelations.findRelations(null, doc, aceDoc);
		// count relation mentions
			int count = aceDoc.relations.size();
		// save count
			relationCount.put(hypo, new Integer(count));
		// get rid of all the entities created by refres
			doc.removeAnnotationsOfType("entity");
			// ------------------- debug
			NEstring.put(hypo, doc.writeSGML("ENAMEX", sentenceSpan));
			String rs = "";
			for (int i=0; i<aceDoc.relations.size(); i++)
				rs += aceDoc.relations.get(i).toString();
			relString.put(hypo, rs);
			// ------------------- debug
		}
	}

}
