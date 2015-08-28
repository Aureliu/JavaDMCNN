// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Lex.*;
import Jet.Scorer.*;
import Jet.Lisp.*;

class HMMtest {

	static SGMLScorer scorer = null;

    public static void main (String[] args) throws IOException {
    	/*
    	HMMTagger tagger2 = new HMMTagger();
    	tagger2.load("data/pos_hmm.txt");
    	tagger2.store("data/pos_hmm_with_state_counts.txt");
    	System.exit(0);
    	*/
		new AnnotationColor("C:\\My Documents\\HMM");

        boolean train = false;

        HMMTagger tagger = new HMMTagger();
        if (train) {
        	tagger.train ("C:\\My Documents\\HMM\\POS Collection.txt");
        	System.out.println ("Writing pos_hmm.txt");
        	tagger.store ("C:\\My Documents\\HMM\\pos_hmm.txt");
        } else {
        	tagger.load ("C:\\My Documents\\HMM\\pos_hmm.txt");
        }
        // ExternalDocument testdoc = new ExternalDocument("sgml", "C:\\My Documents\\HMM\\pos test.txt");
        // ExternalDocument testdoc = new ExternalDocument("sgml", "C:\\My Documents\\HMM\\24.notag");
        ExternalDocument testdoc = new ExternalDocument("sgml", "C:\\My Documents\\HMM\\24head.notag");
		testdoc.open();
		Vector textSegments = testdoc.annotationsOfType ("S");

		System.out.println ("Annotating " + testdoc.fileName());
        if (textSegments == null)
			System.out.println ("No S annotations in document.");
		else {
	      	Iterator it = textSegments.iterator ();
	      	while (it.hasNext ()) {
		        Annotation para = (Annotation)it.next ();
		        Span sentenceSpan = para.span ();
		        Tokenizer.tokenizeOnWS (testdoc, sentenceSpan);
		        // mark first token 'forcedCap'
		        int ic = sentenceSpan.start();
		        while ((ic < sentenceSpan.end()) && Character.isWhitespace(testdoc.charAt(ic))) ic++;
		        Annotation token = testdoc.tokenAt(ic);
		        if (token != null)
		        	token.attributes().put("case", "forcedCap");
		        //
		        tagger.tagPenn (testdoc, sentenceSpan);
		    }
		}

		// display with 'view'
    	new View (testdoc, 1);

    	System.out.println ("Scoring ... ");
    	// ExternalDocument key = new ExternalDocument("pos", "C:\\My Documents\\HMM\\pos key.txt");
    	// ExternalDocument key = new ExternalDocument("pos", "C:\\My Documents\\HMM\\24.tag");
    	ExternalDocument key = new ExternalDocument("pos", "C:\\My Documents\\HMM\\24head.tag");
		key.open();
		new View (key, 2);
		tagger.score (testdoc, key);
	}

}
