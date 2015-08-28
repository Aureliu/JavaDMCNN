// -*- tab-width: 4 -*-
package Jet;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Scorer.*;

/**
 *  runs chunk patterns and scores result against SGML version of
 *  treebank ng chunks.
 */

class ChunkTest {

	static SGMLScorer scorer = null;
	static final String home =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/";
	static String[] SGMLtags = {"S", "ng"};

    public static void main (String[] args) throws IOException {

		// initialize Jet
		System.out.println("Starting ACE Jet...");
		JetTest.initializeFromConfig("chunk ace.properties");
		new Jet.Console();

		ExternalDocument testdoc = new ExternalDocument("sgml", home + "HMM/Chunk/chunk text.txt");
		testdoc.setSGMLtags (SGMLtags);
		testdoc.open();

		System.out.println ("Annotating " + testdoc.fileName());

  		// process document
		Control.processDocument (testdoc, null, true, 1);

    	System.out.println ("Scoring ... ");
    	ExternalDocument key = new ExternalDocument("sgml", home + "HMM/Chunk/chunk key.txt");
		key.setSGMLtags(SGMLtags);
		key.open();
		new View (key, 2);

		scorer = new SGMLScorer(testdoc, key);
        scorer.match("ng");
        System.out.println ("ng in response:  " + scorer.numOfTagsInDoc1);
        System.out.println ("ng in key:       " + scorer.numOfTagsInDoc2);
        System.out.println ("Matching ng:  " + scorer.numOfMatchingTags);
        System.out.println ("Recall:  " +
        	(float) scorer.numOfMatchingTags / scorer.numOfTagsInDoc2);
        System.out.println ("Precision:  " +
        	(float) scorer.numOfMatchingTags / scorer.numOfTagsInDoc1);
	}

}
