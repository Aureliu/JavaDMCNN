// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.60
//Copyright(c): 2011
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Chunk;

import java.util.*;
import java.io.*;
import Jet.JetTest;
import Jet.Tipster.*;
import Jet.Lex.*;
import Jet.Scorer.*;
import Jet.Lisp.*;
import Jet.Zoner.SentenceSplitter;
import Jet.Console;
import AceJet.Ace;	// for monocase flags
import AceJet.Gazetteer;
import Jet.Refres.Resolve;
import Jet.HMM.*;

/**
 *  a Named Entity tagger based on a maximum entropy token classifier.
 */

public class MENameTagger implements NameTagger {

	public MaxEntNE mene;
        HMMannotator annotator;

	/**
	 *  creates a new MENameTagger.
	 */

	public MENameTagger () {
		mene = new MaxEntNE();
		annotator = new HMMannotator(mene);
		annotator.setBItag (true);
		annotator.setAnnotateEachToken (false);
	}

	/**
	 *  initialize the tagger for training by loading from file <CODE>tagTableFile</CODE>
	 *  the list of valid annotations.
	 */

	public void initialize (String tagTableFile, String featureFile) {
		mene.resetForTraining(featureFile);
		annotator.readTagTable (tagTableFile);
	}


	/**
	 *  train the tagger using the collection of Documents 'trainingCollection'.
	 *  The documents should have a TEXT zone marked;  training is done on all sentences
	 *  within this zone.
	 */

	public void train (String trainingCollection) throws IOException {
		DocumentCollection trainCol = new DocumentCollection(trainingCollection);
		trainCol.open();
		for (int i=0; i<trainCol.size(); i++) {
			ExternalDocument doc = trainCol.get(i);
			train (doc);
		}
	}

	public void train (ExternalDocument doc) {
		doc.setAllTags (true);
		doc.open();
		doc.stretchAll();
		System.out.println ("Training from " + doc.fileName());
		mene.newDocument();
		Vector<Annotation> textSegments = doc.annotationsOfType ("TEXT");
		if (textSegments == null) return;
		for (Annotation ann : textSegments) {
			Span textSpan = ann.span ();
			// check document case
			Ace.monocase = Ace.allLowerCase(doc);
			// System.out.println (">>> Monocase is " + Ace.monocase);
			// split into sentences
			SentenceSplitter.split (doc, textSpan);
		}
		Vector<Annotation> sentences = doc.annotationsOfType ("sentence");
		if (sentences == null) return;
		for (Annotation sentence : sentences) {
			Span sentenceSpan = sentence.span();
			Tokenizer.tokenize (doc, sentenceSpan);
			Lexicon.annotateWithDefinitions(doc, sentenceSpan.start(), sentenceSpan.end());
			annotator.trainOnSpan (doc, sentenceSpan);
		}
		//  free up space taken by annotations on document
		doc.clearAnnotations();
	}

	public void train (String directory, String fileList) throws IOException {
                BufferedReader reader = new BufferedReader (new FileReader(fileList));
                int docCount = 0;
                String currentDoc;
                while ((currentDoc = reader.readLine()) != null) {
                        docCount++;
                        System.out.println ("\nTraining from document " + docCount + ": " + currentDoc);
                        ExternalDocument doc = new ExternalDocument("sgml", directory, currentDoc);
                        train (doc);
                }
	}

	/**
	 *  store the data associated with this tagger to file 'fileName'.
	 *  This data consists of the tag tables (recording the different annotations
	 *  which may be assigned by this tagger) and the data used by the
	 *  max ent token classifier.
	 */

	public void store (String fileName) throws IOException {
		BufferedWriter out = new BufferedWriter
			(new OutputStreamWriter
			 (new FileOutputStream(fileName), JetTest.encoding));
		annotator.writeTagTable (out);
		out.write ("endtags");
		out.newLine ();
		mene.store (out);
	}

	/**
	 *  load the data associated with this tagger from file 'fileName'.
	 */

	public void load (String fileName) throws IOException {
		BufferedReader in = new BufferedReader
			(new InputStreamReader
			 (new FileInputStream(fileName), JetTest.encoding));
		annotator.readTagTable(in);
		mene.load(in);
	}

	/**
	 *  tag document <CODE>doc</CODE> for named entities.
	 */

	public void tagDocument (Document doc) {
		mene.newDocument();
		Vector textSegments = doc.annotationsOfType ("TEXT");
		Iterator it = textSegments.iterator ();
		while (it.hasNext ()) {
			Annotation ann = (Annotation)it.next ();
			Span textSpan = ann.span ();
			SentenceSplitter.split (doc, textSpan);
		}
		Vector sentences = doc.annotationsOfType ("sentence");
		Iterator is = sentences.iterator ();
		while (is.hasNext ()) {
			Annotation sentence = (Annotation)is.next ();
			Span sentenceSpan = sentence.span();
			Tokenizer.tokenize (doc, sentenceSpan);
			Lexicon.annotateWithDefinitions(doc, sentenceSpan.start(), sentenceSpan.end());
			tag (doc, sentenceSpan);
		}
	}

	public void newDocument () {
		mene.newDocument();
	}

	/**
	 *  tag span 'span' of Document 'doc' with Named Entity annotations.
	 */

	public void tag (Document doc, Span span) {
		if (HMMNameTagger.inZone(doc, span, "POSTER") || HMMNameTagger.inZone(doc, span, "SPEAKER"))
                        HMMNameTagger.tagPersonZone (doc, span, annotator);
                else {
                        annotator.annotateSpan (doc, span);
			// special code for SRI - 11 Aug 2013
			Onoma.tagDrugs (doc, span);
		}
	}

       // config file must specify dictionaries, gazetteer, word-clusters

	public static void main (String[] args) throws IOException {
                if (args.length < 6 || args.length % 2 == 1) {
                        System.err.println ("MENameTagger requires 4 + 2n arguments for n training corpora:");
                        System.err.println ("  state-file feature-file model-file props-file directory1 filelist1 [directory2 filelist2] ...");
                        System.exit (1);
                }
                String stateFile = args[0];
                String featureFile = args[1];
                String modelFile = args[2];
                String configFile = args[3];
		JetTest.initializeFromConfig (configFile);
                MENameTagger nt = new MENameTagger();
		nt.initialize (stateFile, featureFile);
		for (int pass=1; pass <= 2; pass++) {
			MaxEntNE.pass = pass;
			MaxEntNE.trainingDocCount = 0;
                	for (int iarg = 4; iarg<args.length; iarg+=2) {
                        	String directory = args[iarg];
                        	String fileList = args[iarg+1];
                        	nt.train (directory, fileList);
			}
                }
		nt.mene.createModel();
                nt.store(modelFile);
	}
}
