// -*- tab-width: 4 -*-
package Jet.Refres;

import java.util.*;
import java.io.*;

import Jet.JetTest;
import Jet.Control;
import Jet.Console;
import Jet.Validate;
import Jet.Pat.Pat;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Chunk.Chunker;

import AceJet.Gazetteer;
import AceJet.Ace;

/**
 *  evaluate reference resolution procedure against a key file
 *  with references annotated.
 */

public class CorefEval {

	// static final String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
	// static final String ACEdir = home + "ACE/";
	// static final String collection = ACEdir + "training nwire sgm 10.txt";
	// static final String keyCollection = ACEdir + "training nwire coref 10.txt";
	// static final String parseCollection = ACEdir + "parses/training nwire 10 parses.txt";
	static final String collection = "coref/training nwire sgm.txt";
	static final String keyCollection = "coref/training nwire coref.txt";
	static final String parseCollection = "coref/training nwire parses.txt";
	static final String baselineCollection = "temp/coref/coref baseline.txt";

	static final boolean writeBaseline = false;
	static final boolean compareToBaseline = false;
	static final boolean useParser = true;
	/**
	 *  useParseCollection:  use already-parsed documents
	 */
	static final boolean useParseCollection = true;
	/**
	 *  true to evaluate pronoun accuracy
	 *  false to evaluate overall coref accuracy (MUC metric)
	 */
	static final boolean onlyPronouns = true;

	public static void main (String[] args) throws IOException {
		// initialize Jet
		System.out.println("Starting ACE Jet...");
		if (useParser) {
			if (useParseCollection) {
				JetTest.initializeFromConfig("props/ace 05 use parses.properties");
			} else {
				JetTest.initializeFromConfig("props/ace parser.properties");
			}
		} else {
			JetTest.initializeFromConfig("props/ME ace 05.properties");
		}
		new Console();
		// load ACE type dictionary
		AceJet.EDTtype.readTypeDict();
		// ACE mode (provides additional antecedents ...)
		Resolve.ACE = true;
		// select rule based / statistical resolver
		Resolve.useMaxEnt = true;
		// open text/parse and key collections
		DocumentCollection col;
		if (useParser & useParseCollection) {
			col = new DocumentCollection(parseCollection);
		} else {
			col = new DocumentCollection(collection);
		}
		DocumentCollection keyCol = new DocumentCollection(
			compareToBaseline ? baselineCollection : keyCollection);
		col.open();
		keyCol.open();
		DocumentScorer scorer = onlyPronouns ? new PronounScorer() : new CorefScorer();
		for (int docCount = 0; docCount < col.size(); docCount++) {
			ExternalDocument doc = col.get(docCount);
			System.out.println ("\nProcessing document " + docCount + ": " + doc.fileName());
			Console.println ("\nProcessing document " + docCount + ": " + doc.fileName());
			// read test document
			doc.setAllTags(true);
			doc.open();
			// process document
			AceJet.Ace.monocase = AceJet.Ace.allLowerCase(doc);
			Control.processDocument (doc, null, docCount == -1, docCount);
			Ace.tagReciprocalRelations(doc);
			// read coref key
			ExternalDocument keyDoc = keyCol.get(docCount);
			keyDoc.setAllTags(true);
			keyDoc.open();
			keyDoc.stretch("mention");
			// create 'entity' annotations for key
			CorefFilter.buildEntitiesFromMentions(keyDoc);
			// score document
			scorer.score(doc, keyDoc);
			// report results for document
			scorer.report();
			if (writeBaseline) {
				CorefFilter.buildMentionsFromEntities(doc);
				doc.removeAnnotationsOfType ("token");
				doc.removeAnnotationsOfType ("constit");
				doc.removeAnnotationsOfType ("tagger");
				doc.removeAnnotationsOfType ("ENAMEX");
				doc.removeAnnotationsOfType ("entity");
				doc.removeAnnotationsOfType ("ng");
			}
		}
		scorer.summary();
		if (writeBaseline) {
			col.saveAs (baselineCollection);
		} else {
			// CorefCompare.compareCollections(col, keyCol);
			new CollectionView (col);
		}
	}

	/**
	 *  command-line callable coreference evaluation (invoked by jet -CorefEval).
	 *  Passed an array of two file names:  the system response collection and
	 *  the key collection.
	 **/

	public static void task (String[] args) {
		if (args.length != 3) {
			System.out.println
			  ("CorefEval requires 2 arguments: jet -CorefEval <response collection> <key collection>");
			System.exit(1);
		}
		// open text and key collections
		String collection = args[1]; // ACEdir + "training nwire bad coref 10.txt";
		String keyCollection = args[2]; // ACEdir + "training nwire coref 10.txt";
		DocumentCollection col = new DocumentCollection(collection);
		DocumentCollection keyCol = new DocumentCollection(keyCollection);
		col.open();
		keyCol.open();
		CorefScorer scorer = new CorefScorer();
		for (int docCount = 0; docCount < col.size(); docCount++) {
			// process file 'currentDoc'
			// if (docCount > 1) continue;
			ExternalDocument doc = col.get(docCount);
			System.out.println ("\nProcessing document " + docCount + ": " + doc.fileName());
			// read test document
			doc.setAllTags(true);
			doc.open();
			// read coref key
			ExternalDocument keyDoc = keyCol.get(docCount);
			keyDoc.setAllTags(true);
			keyDoc.open();
			// create 'entity' annotations
			CorefFilter.buildEntitiesFromMentions(doc);
			CorefFilter.buildEntitiesFromMentions(keyDoc);
			// score document
			scorer.score(doc, keyDoc);
			scorer.report();
		}
		scorer.summary();
		float FO = 2.f / (1.f / scorer.overallRecall + 1.f / scorer.overallPrecision);
		Validate.score = FO;
	}


}
