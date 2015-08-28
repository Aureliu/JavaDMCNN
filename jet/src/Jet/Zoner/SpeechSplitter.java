// -*- tab-width: 4 -*-
package Jet.Zoner;

import java.util.*;
import Jet.Tipster.*;

/**
 *  a sentence splitter for ASR (transcribed speech) files.  It assumes that
 *  each word is preceded by a zero-width annotation of type 'W' with features
 *  'Bsec' (start time, in seconds) and 'Dur' (duration, in seconds).
 *  We assume that any gap of at least 'SENTENCE_GAP' seconds is a sentence
 *  break.
 */

public class SpeechSplitter {

	static final double SENTENCE_GAP = 0.25;
	static boolean trace = true;

	public static void split (Document doc, Span textSpan) {
    int start = textSpan.start();
    int end = textSpan.end();
    double lastEndTime = -1.;
    Vector Ws = doc.annotationsOfType("W");
    for (int iw=0; iw<Ws.size(); iw++) {
    	Annotation W = (Annotation) Ws.get(iw);
    	String BsecStg = (String) W.get("Bsec");
    	String DurStg = (String) W.get("Dur");
    	double Bsec, Dur;
    	try {
    		Bsec = Double.parseDouble(BsecStg);
    		Dur = Double.parseDouble(DurStg);
    	} catch (NumberFormatException e) {
    		System.out.println ("Speech splitter:  ill-formed W annotation " + W);
    		return;
    	}
    	if (lastEndTime >= 0 && Bsec > lastEndTime + SENTENCE_GAP) {
    		int posn = W.start();
    		Span span = new Span (start, posn);
    		doc.annotate("sentence", span, null);
    		if (trace)
    			System.out.println ("Sentence: " + doc.text(span).replace('\n',' '));
    		start = posn;
    	}
    	lastEndTime = Bsec + Dur;
    }
    if (lastEndTime >= 0) {
    	Span span = new Span (start, end);
  		doc.annotate("sentence", span, null);
  		if (trace)
  			System.out.println ("Sentence: " + doc.text(span).replace('\n',' '));
    }
  }

  static final String ACEdir =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/ACE/";
  static final String collectionFile = ACEdir + "asr pilot sgm.txt";

  public static void main (String[] args) {
  	// open collection
		DocumentCollection collection = new DocumentCollection(collectionFile);
		collection.open();
  	// for each doc in collection
  	for (int i=0; i<collection.size(); i++) {
			// open document
			ExternalDocument doc = collection.get(i);
			System.out.println ("Processing document " + doc.fileName());
			doc.setAllTags (true);
			doc.setEmptyTags(new String[] {"W"});
			doc.open();
			doc.annotateWithTag ("text");
			Span textSpan = ((Annotation) doc.annotationsOfType ("TEXT").get(0)).span();
  		// run sentence splitter
  		split (doc, textSpan);
  	}
  }

}
