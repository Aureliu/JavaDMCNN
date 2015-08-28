// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2003, 2004, 2005
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
import Jet.Zoner.*;
import Jet.Console;
import AceJet.Ace;	// for monocase flags

/**
 *  a Named Entity tagger based on the generic HMM (Hidden Markov Model) mechanism.
 *  Methods are provided for creating an HMM for a set of name tags, for training
 *  the HMM from annotated corpora, for applying the tagger to new text, and for
 *  scoring the results.
 *
 *  It uses an external file consisting of a tag table, the line 'endtags', and
 *  the HMM.
 */

public class HMMNameTagger implements NameTagger {

	public HMM nameHMM;
	public HMMannotator annotator;
	String[][] tagTable;
	String[] NEtypeTable;
	String[] tagsToRead;
	String[] tagsToCache;
	static String[] tagsToScore;
	Class emitterClass;

	/**
	 *  creates a new HMMNameTagger (with an empty HMM).
	 *  @param emitterClass  the class of the emitter associated with each state of
	 *                       the HMM;  must be a subclass of <CODE>HMMemitter</CODE>.
	 */

	public HMMNameTagger (Class emitterClass) {
		if (!HMMemitter.class.isAssignableFrom(emitterClass)) {
			System.out.println ("HMMNameTagger constructor invoked with invalid class " + emitterClass);
			return;
		}
		this.emitterClass = emitterClass;
		nameHMM = new HMM(emitterClass);
		annotator = new HMMannotator(nameHMM);
		annotator.setBItag (false);
		annotator.setAnnotateEachToken (false);
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
  	HashSet<String> annotationTypes = new HashSet<String>();
    ArrayList<String> hmmTagList = new ArrayList<String>();
		String[][] tagTable = annotator.getTagTable();
		for (int i=0; i<tagTable.length; i++) {
			annotationTypes.add(tagTable[i][0]);
		  hmmTagList.add(tagTable[i][3]);
		}
    NEtypeTable = (String[]) hmmTagList.toArray(new String[0]);
    tagsToCache = NEtypeTable;
    nameHMM.setTagsToCache (tagsToCache);
    // tagsToScore = (String[]) annotationTypes.toArray(new String[0]);
    tagsToScore = new String[]{"ENAMEX"};
    annotationTypes.add("SENT");
    annotationTypes.add("TURN");
    // tagsToRead = (String[]) annotationTypes.toArray(new String[0]);
    tagsToRead = new String[] {"ENAMEX", "VALUE", "TIMEX"};
  }

  private void writeTagTable (BufferedWriter bw) {
  	annotator.writeTagTable(bw);
  }

  /**
   *  create the HMM states and arcs.  This is the first step in building
   *  the HMM, prior to training.  The list of annotation types and features
   *  is taken from file 'tagFileName'. <p>
   *  Creates six states for each name type.
   */

	public void buildNameHMM (String tagFileName) {
		readTagTable (tagFileName);
		HMMstate startState = new HMMstate("start", "", emitterClass);
		nameHMM.addState(startState);
		startState.addArc("other");
		startState.addArc("end");
		for (int j=0; j<NEtypeTable.length; j++) {
			startState.addArc("pre-" + NEtypeTable[j]);
			startState.addArc("i-" + NEtypeTable[j]);
			startState.addArc("b-" + NEtypeTable[j]);
		}
		HMMstate otherState = new HMMstate("other", "other", emitterClass);
		nameHMM.addState(otherState);
		otherState.addArc("other");
		otherState.addArc("end");
		for (int j=0; j<NEtypeTable.length; j++) {
			otherState.addArc("pre-" + NEtypeTable[j]);
		}
		HMMstate endState = new HMMstate("end", "", emitterClass);
		nameHMM.addState(endState);
		for (int i=0; i<NEtypeTable.length; i++) {
			String NEtype = NEtypeTable[i];
			// pre-T goes to i-T and b-T
			HMMstate preState = new HMMstate("pre-" + NEtype, "other", emitterClass);
			nameHMM.addState(preState);
			preState.addArc("i-" + NEtype);
			preState.addArc("b-" + NEtype);
			// i-T state goes to post-T, pre-T', i-T', and b-T'
			HMMstate iState = new HMMstate("i-" + NEtype, NEtype, emitterClass);
			nameHMM.addState(iState);
			// b-T state goes to m-T and e-T
			HMMstate bState = new HMMstate("b-" + NEtype, NEtype, emitterClass);
			nameHMM.addState(bState);
			bState.addArc("m-" + NEtype);
			bState.addArc("e-" + NEtype);
			// m-T state goes to m-T and e-T
			HMMstate mState = new HMMstate("m-" + NEtype, NEtype, emitterClass);
			nameHMM.addState(mState);
			mState.addArc("m-" + NEtype);
			mState.addArc("e-" + NEtype);
			// e-T state goes to post-T, pre-T', i-T', and b-T'
			HMMstate eState = new HMMstate("e-" + NEtype, NEtype, emitterClass);
			nameHMM.addState(eState);
			// post-T state goes to other and pre-T'
			HMMstate postState = new HMMstate("post-" + NEtype, "other", emitterClass);
			nameHMM.addState(postState);
			for (int j=0; j<NEtypeTable.length; j++) {
				iState.addArc("pre-" + NEtypeTable[j]);
				eState.addArc("pre-" + NEtypeTable[j]);
				postState.addArc("pre-" + NEtypeTable[j]);
				if (i != j) {
					iState.addArc("i-" + NEtypeTable[j]);
					iState.addArc("b-" + NEtypeTable[j]);
					eState.addArc("i-" + NEtypeTable[j]);
					eState.addArc("b-" + NEtypeTable[j]);
				}
			}
			iState.addArc ("post-" + NEtype);
			iState.addArc ("end");
			eState.addArc ("post-" + NEtype);
			eState.addArc ("end");
			postState.addArc ("other");
			postState.addArc ("end");
		}
		nameHMM.resolveNames();
		nameHMM.resetForTraining();
		return;
	}

	/**
	 *  train the HMMNameTagger using the collection of Documents 'trainingCollection'.
	 *  The documents should have a TEXT zone marked;  training is done on all sentences
	 *  within this zone.
	 */

	public void train (String trainingCollection) throws IOException {
		DocumentCollection trainCol = new DocumentCollection(trainingCollection);
		trainCol.open();
		for (int i=0; i<trainCol.size(); i++) {
			ExternalDocument doc = trainCol.get(i);
			System.out.println ("\nTraining from document " + (i+1) + ": " + doc.fileName());
			train (doc);
		}
		nameHMM.computeProbabilities();
	}
	
	public void train (String directory, String fileList) throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader(fileList));
		int docCount = 0;
		String currentDoc;
		while ((currentDoc = reader.readLine()) != null) {
			docCount++;
			System.out.println ("\nTraining from document " + docCount + ": " + currentDoc);
			String neFileName = directory + currentDoc;
			ExternalDocument doc = new ExternalDocument("sgml", neFileName);
			train (doc);
		}
		nameHMM.computeProbabilities();
	}	
	
	public void train (ExternalDocument doc) throws IOException {
		doc.setAllTags (true);
		doc.open();
		doc.stretchAll();
		doc.annotateWithTag ("TEXT");
		SpecialZoner.findSpecialZones (doc);
		nameHMM.newDocument();
		Vector textSegments = doc.annotationsOfType ("TEXT");
		if (textSegments == null) {
			System.out.println ("No <TEXT> in " + doc.fileName() + ", skipped.");
			return;
		}
		Iterator it = textSegments.iterator ();
		while (it.hasNext ()) {
			Annotation ann = (Annotation)it.next ();
			Span textSpan = ann.span ();
			// check document case
			Ace.monocase = Ace.allLowerCase(doc);
			System.out.println (">>> Monocase is " + Ace.monocase);
			// split into sentences
			SentenceSplitter.split (doc, textSpan);
		}
		Vector sentences = doc.annotationsOfType ("sentence");
		if (sentences == null) return;
		Iterator is = sentences.iterator ();
		while (is.hasNext ()) {
			Annotation sentence = (Annotation)is.next ();
			Span sentenceSpan = sentence.span();
			Ace.monocase = Ace.allLowerCase(doc, sentenceSpan) || Ace.titleCase(doc, sentenceSpan); //<<
			Tokenizer.tokenize (doc, sentenceSpan);
			annotator.trainOnSpan (doc, sentenceSpan);
		}
		//  free up space taken by annotations on document
		doc.clearAnnotations();
	}


	/**
	 *  store the tag table and the HMM associated with this tagger
	 *  to file 'fileName'.
	 */

	public void store (String fileName) throws IOException {
		BufferedWriter bw =
		  new BufferedWriter
				(new OutputStreamWriter
					(new FileOutputStream (fileName), JetTest.encoding));
		writeTagTable (bw);
		bw.write ("endtags");
		bw.newLine ();
		nameHMM.store (new PrintWriter(bw));
	}

	/**
	 *  load the tag table and the HMM associated with this tagger
	 *  from file 'fileName'.
	 */

	public void load (String fileName) throws IOException {
		BufferedReader in = new BufferedReader
			(new InputStreamReader
				(new FileInputStream(fileName), JetTest.encoding));
		readTagTable(in);
		nameHMM.load(in);
	}

	public void tagDocument (Document doc) {
		nameHMM.newDocument();
		doc.annotateWithTag ("TEXT");
		SpecialZoner.findSpecialZones (doc);
	    Vector textSegments = doc.annotationsOfType ("TEXT");
	    Iterator it = textSegments.iterator ();
	    while (it.hasNext ()) {
	        Annotation ann = (Annotation)it.next ();
	        Span textSpan = ann.span ();
	        // check document case
					Ace.monocase = Ace.allLowerCase(doc);
					System.out.println (">>> Monocase is " + Ace.monocase);
	        SentenceSplitter.split (doc, textSpan);
	    }
	    Vector sentences = doc.annotationsOfType ("sentence");
	    /*
	    doc.annotateWithTag("slug");
	    doc.annotateWithTag("nwords");
	    doc.annotateWithTag("preamble");
	    doc.annotateWithTag("trailer");
	    sentences.addAll(doc.annotationsOfType("slug"));
	    sentences.addAll(doc.annotationsOfType("nwords"));
	    sentences.addAll(doc.annotationsOfType("preamble"));
	    sentences.addAll(doc.annotationsOfType("trailer"));
	    */
    	Iterator is = sentences.iterator ();
    	while (is.hasNext ()) {
	    	Annotation sentence = (Annotation)is.next ();
	    	Span sentenceSpan = sentence.span();
	    	Ace.monocase = Ace.allLowerCase(doc, sentenceSpan) || Ace.titleCase(doc, sentenceSpan); //<<
				// System.out.println (">>> Monocase is " + Ace.monocase);
	    	Tokenizer.tokenize (doc, sentenceSpan);
	    	tag (doc, sentenceSpan);
		}
	}

	public void newDocument () {
		nameHMM.newDocument();
	}

	/**
	 *  tag span 'span' of Document 'doc' with Named Entity annotations.
	 */

	public void tag (Document doc, Span span) {
   	// Ace.monocase = Ace.allLowerCase(doc, span) || Ace.titleCase(doc, span); //<<
		if (inZone(doc, span, "POSTER") || inZone(doc, span, "SPEAKER"))
			tagPersonZone (doc, span, annotator);
		else
			annotator.annotateSpan (doc, span);
	}
	
	/**
	 *  returns 'true' if Span 'span' is enclosed in an annotation of type
	 *  'zoneType'.  This function is used to detect special markup in
	 *  the text, such as 'SPEAKER', which indicates a name of a particular
	 *  type.
	 */

	public static boolean inZone (Document doc, Span span, String zoneType) {
		// get first non-whitespace character of span
		String text = doc.text();
		int posn = span.start();
		int end = span.end();
		while ((posn < end) && Character.isWhitespace(text.charAt(posn))) posn++;
		// get set of zones
		Vector zones = doc.annotationsOfType(zoneType);
		if (zones == null) return false;
		for (int i=0; i<zones.size(); i++) {
			Annotation zone = (Annotation) zones.get(i);
			Span zoneSpan = zone.span();
			if (posn >= zoneSpan.start() && posn < zoneSpan.end())
				return true;
		}
		return false;
	}

	public static void tagPersonZone (Document doc, Span span, HMMannotator annotator) {
		// skip leading whitespace
		String text = doc.text();
		int start = span.start();
		int end = span.end();
		while ((start < end) && Character.isWhitespace(text.charAt(start))) start++;
		// look for comma
		int comma = start;
		while ((comma < end) && text.charAt(comma) != ',') comma++;
		// no comma, tag entire string (pos, end) as name
		if (comma >= end) {
			Span s = new Span(start, end);
			doc.annotate("ENAMEX", s, new FeatureSet ("TYPE", "PERSON"));
		} else {
		// comma, tag (pos, comma) as name;  tag (comma, end) using HMM
			Span sName = new Span(start, comma);
			Span sRest = new Span(comma, end);
			if (comma > start)
				doc.annotate("ENAMEX", sName, new FeatureSet ("TYPE", "PERSON"));
			if (end > comma)				
				annotator.annotateSpan (doc, sRest);
		}
	}

	final static String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
	static final String ACEdir = home + "ACE 05/V4/";
	static boolean useAceBigrams = false;

	/**
	 *  procedure the named entity tagger on documents with in-line NE tags. Takes
	 *  3+2n arguments:
	 *  <ul>
	 *  <li> state-file:  file defining states of the HMM (input)
	 *  <li> model-file:  file containing trained HMM model (output)
	 *  <li> unigram|bigram:  should model use unigram or bigram probabilities
	 *  <li> directory-1:  directory containing ne-tagged files
	 *  <li> filelist-1:   list of ne-tagged files
	 *  <li> directory-2
	 *  <li> filelist-2
	 *  <li> ...
	 *  <ul>
	 */

	public static void main (String[] args) throws IOException {
		if (args.length < 5 || args.length % 2 == 0) {
			System.err.println ("HMMNameTagger requires 3 + 2n arguments for n training corpora:");
			System.err.println ("  state-file model-file uni/bigram directory1 filelist1 [directory2 filelist2] ...");
			System.exit (1);
		}
		new AnnotationColor(ACEdir);
		String stateFile = args[0];
		String modelFile = args[1];
		useAceBigrams = args[2].equals("bigram");
		HMMNameTagger nt = new HMMNameTagger(
			useAceBigrams ? BigramHMMemitter.class : WordFeatureHMMemitter.class);
		nt.buildNameHMM(stateFile);
		for (int iarg = 3; iarg<args.length; iarg+=2) {
			String directory = args[iarg];
			if (!directory.endsWith("/")) directory += "/";
			String fileList = args[iarg+1];
			nt.train (directory, fileList);
		}
		nt.store(modelFile);		
		
		// mucTrainTest ();
		// mucLoadTest ();
		// aceTrainTest ();
		// aceLoadTest ();
		// ace05TrainTest();
		// ace05LoadTest();
		// galeLoadTest ();
		// galeTrainTest();
	}

	static void aceTrainTest () throws IOException {
		HMMNameTagger nt = new HMMNameTagger(
			useAceBigrams ? BigramHMMemitter.class : WordFeatureHMMemitter.class);
		nt.buildNameHMM("acedata/ACEnameTags.txt");
		String trainingCollection1 =  home + "HMM/NE/ACE BBN Collection.txt";
		String trainingCollection2 =  home + "HMM/NE/ACE training Collection.txt";
		String trainingCollection3 =  home + "HMM/NE/ACE aug03 Collection.txt";
		String trainingCollection4 =  home + "ACE/training04 nwire 21andup ne.txt";
		String trainingCollection5 =  home + "ACE/training04 bnews 21andup ne.txt";
		nt.train (trainingCollection1);
		nt.train (trainingCollection2);
		// nt.train (trainingCollection3); removed -- not helpful
		nt.train (trainingCollection4);
		nt.train (trainingCollection5);
		if (useAceBigrams)
			nt.store ("acedata/ACEname04bigramHMM.txt");
		else
			nt.store ("acedata/ACEname04HMM.txt");
		aceTest (nt);
	}

	private static void aceLoadTest () throws IOException {
		HMMNameTagger nt = new HMMNameTagger(
			useAceBigrams ? BigramHMMemitter.class : WordFeatureHMMemitter.class);
		if (useAceBigrams)
			nt.load ("acedata/ACEname04bigramHMM.txt");
		else
			nt.load ("acedata/ACEname04HMM.txt");
		// nt.load (home + "HMM/NE/ACEname04HMM.txt");
		// nt.load (home + "HMM/NE/ACEname04bigramHMM.txt");
		// nt.load (home + "HMM/NE/ACEname04bigramxHMM.txt");
		// nt.load (home + "HMM/NE/ACEname04extendedHMM.txt");
		aceTest (nt);
	}

	private static void aceTest (HMMNameTagger nt) throws IOException {
		// String testCollection = home + "HMM/NE/ACE test Collection.txt";
		// String testCollection = home + "HMM/NE/ACE sep02 text Collection.txt";
		// String testCollection = home + "HMM/NE/ACE sep02 nwire text Collection.txt";
		String testCollection = home + "ACE/training04 nwire 20 sgm.txt";
		// String testCollection = home + "ACE/training04 bnews 20 sgm.txt";
		// String testCollection = home + "ACE/rdreval04 nwire sgm.txt";
		// String keyCollection = home + "HMM/NE/ACE key Collection.txt";
		// String keyCollection = home + "HMM/NE/ACE sep02 Collection.txt";
		// String keyCollection = home + "HMM/NE/ACE sep02 nwire Collection.txt";
		String keyCollection = home + "ACE/training04 nwire 20 ne.txt";
		// String keyCollection = home + "ACE/training04 bnews 20 ne.txt";
		// String keyCollection = home + "ACE/rdreval04 nwire ne.txt";
		BigramHMMemitter.useBigrams = false;
		NEScorer.scoreCollection (nt, testCollection, keyCollection, tagsToScore);
	}

	static void ace05TrainTest () throws IOException {
		HMMNameTagger nt = new HMMNameTagger(
			useAceBigrams ? BigramHMMemitter.class : WordFeatureHMMemitter.class);
		nt.buildNameHMM("acedata/ACE05nameTags.txt");
		String trainingCollection1 =  ACEdir + "NE/tailNE.txt";
		String trainingCollection4 =  home + "ACE/training04 nwire 21andup ne.txt";
		String trainingCollection5 =  home + "ACE/training04 bnews 21andup ne.txt";
		nt.train (trainingCollection1);
		nt.train (trainingCollection1);
		nt.train (trainingCollection4);
		nt.train (trainingCollection5);
		if (useAceBigrams)
			nt.store ("acedata/ACEname05bigramHMM.txt");
		else
			nt.store ("acedata/ACEname05HMM.txt");
		ace05Test (nt);
	}

	private static void ace05LoadTest () throws IOException {
		HMMNameTagger nt = new HMMNameTagger(
			useAceBigrams ? BigramHMMemitter.class : WordFeatureHMMemitter.class);
		if (useAceBigrams)
			nt.load ("acedata/ACEname05bigramHMM.txt");
		else
			nt.load ("acedata/ACEname05HMM.txt");
		ace05Test (nt);
	}

	private static void ace05Test (HMMNameTagger nt) throws IOException {
		String testCollection = ACEdir + "NE/headSgm.txt";
		String keyCollection = ACEdir + "NE/headNE.txt";
		BigramHMMemitter.useBigrams = false;
		NEScorer.scoreCollection (nt, testCollection, keyCollection, tagsToScore);
	}

	private static void mucTrainTest () throws IOException {
		HMMNameTagger nt = new HMMNameTagger(WordFeatureHMMemitter.class);
		nt.buildNameHMM("data/MUCnameTags.txt");
		// includes official training plus BBN/NYU training
		String trainingCollection =  home + "HMM/NE/NE train Collection.txt";
		nt.train (trainingCollection);
		// nt.store ("C:\\My Documents\\HMM\\NE\\nameHMM.txt");
		nt.store ("data/MUCnameHMM.txt");
		mucTest (nt);
	}

	private static void mucLoadTest () throws IOException {
		HMMNameTagger nt = new HMMNameTagger(WordFeatureHMMemitter.class);
		nt.buildNameHMM("data/MUCnameTags.txt");
		nt.load ("data/MUCnameHMM.txt");
		mucTest (nt);
	}

	private static void mucTest (HMMNameTagger nt) throws IOException {
		// dryrun test
		String testCollection = home + "HMM/NE/NE test Collection.txt";
		String keyCollection = home + "HMM/NE/NE key Collection.txt";
		NEScorer.scoreCollection (nt, testCollection, keyCollection, tagsToScore);
	}

	static void galeTrainTest () throws IOException {
		HMMNameTagger nt = new HMMNameTagger(
			useAceBigrams ? BigramHMMemitter.class : WordFeatureHMMemitter.class);
		nt.buildNameHMM("acedata/ACE05nameTags.txt");
		String trainingCollection1 =  home + "HMM/NE/ACE BBN Collection.txt";
		String trainingCollection2 =  home + "HMM/NE/ACE training Collection.txt";
		String trainingCollection3 =  home + "HMM/NE/ACE aug03 Collection.txt";
		String trainingCollection4 =  home + "ACE/training04 nwire 21andup ne.txt";
		String trainingCollection5 =  home + "ACE/training04 bnews 21andup ne.txt";
		String trainingCollection6 =  ACEdir + "NE/tailNE.txt";
		String trainingCollection7 =  home + "ACE 05/names/NYTfilelist.txt";
		String trainingCollection8 =  home + "ACE 05/names/AFPfilelist.txt";
		String trainingCollection9 =  home + "Ace 07/ET/NE/filelist.sgm";
		nt.train (trainingCollection1);
		nt.train (trainingCollection2);
		// nt.train (trainingCollection3);  not helpful
		nt.train (trainingCollection4);
		nt.train (trainingCollection5);
		nt.train (trainingCollection6);
		nt.train (trainingCollection6);
		nt.train (trainingCollection7);
		// nt.train (trainingCollection8);  not helpful
		nt.train (trainingCollection9);
		if (useAceBigrams)
			nt.store ("acedata/AceNameBigram07HMM.txt");
		else
			nt.store ("acedata/AceName07HMM.txt");
		ace05Test (nt);
	}
	
	static void galeLoadTest () throws IOException {
		HMMNameTagger nt = new HMMNameTagger(
			useAceBigrams ? BigramHMMemitter.class : WordFeatureHMMemitter.class);
		if (useAceBigrams)
			nt.load ("acedata/ACEname06bigramHMM.txt");
		else
			nt.load ("acedata/ACEname06HMM.txt");
		ace05Test (nt);
	}		

}
