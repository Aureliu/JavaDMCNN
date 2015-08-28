// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Lex.*;
import Jet.Scorer.*;
import Jet.Lisp.*;

/**
 *  a POS (part-of-speech) tagger using a bigram model.  The tagger makes
 *  use of the generic HMM (Hidden Markov Model) mechanism.
 */

public class HMMTagger {

	/**
	 *  if true, use HMMannotator trace to write a one-line message about each
	 *  part-of-speech assignment to Console.
	 */

	public static boolean trace = false;

	static SGMLScorer scorer = null;

	static String[] posTable =
    	{"CC", "CD", "DT", "EX", "FW", "IN", "JJ", "JJR", "JJS", "LS", "MD",
		 "NN", "NNS", "NNP", "NNPS", "PDT", "POS", "PRP", "PRP$", "RB",
		 "RBR", "RBS", "RP", "SYM", "TO", "UH", "VB", "VBD", "VBG",
		 "VBN", "VBP", "VBZ", "WDT", "WP", "WP$", "WRB", "#", "$", ".",
		 ",", ":", "(", ")", "`", "``", "'", "''", "-LRB-", "-RRB-"};

	HMM posh;
	HMMannotator annotator;
	String[][] tagTable;

	/**
	 *  create a new HMM-based part-of-speech tagger.
	 */

	public HMMTagger () {
        posh = new HMM(WordFeatureHMMemitter.class);
        annotator = null;
        tagTable = new String[posTable.length][];
    }

    /**
     *  train the tagger using the DocumentCollection in file 'trainingCollection'.
     *  'trainingCollection' should consist of documents which have been explicitly
     *  tagged with part-of-speech information.
     */

	void train (String trainingCollection) {

		for (int i=0; i<posTable.length; i++)
			tagTable[i] = new String[] {"constit", "cat", posTable[i], posTable[i]};

	    // build ergodic HMM with one state for each POS (plus start and end states)

	    HMMstate startState = new HMMstate("start", "", WordFeatureHMMemitter.class);
	    posh.addState(startState);
	    for (int j=0; j<posTable.length; j++)
	    		 startState.addArc(new HMMarc (posTable[j], 0));
	    HMMstate endState = new HMMstate("end", "", WordFeatureHMMemitter.class);
	    posh.addState(endState);
	    for (int i=0; i<posTable.length; i++) {
	    	String pos = posTable[i];
	    	HMMstate state = new HMMstate(pos, pos, WordFeatureHMMemitter.class);
	    	posh.addState(state);
	    	for (int j=0; j<posTable.length; j++)
	    		 state.addArc(new HMMarc (posTable[j], 0));
	    	state.addArc (new HMMarc("end", 0));
	    }
	    posh.resolveNames();

		posh.resetForTraining();
		annotator = new HMMannotator(posh);
		annotator.setTagTable (tagTable);
		annotator.setBItag (false);

		DocumentCollection col = new DocumentCollection(trainingCollection);
		col.open();
		for (int i=0; i<col.size(); i++) {
			ExternalDocument doc = col.get(i);
			doc.open();
			System.out.println ("Training from " + doc.fileName());

			// divide at endmarks (constit cat="."), adding "S" marks

			int posn = 0;
			int start = posn;
			Vector anns;
			while ((anns = doc.annotationsAt(posn, "constit")) != null) {
				Annotation ann = (Annotation) anns.get(0);
				posn = ann.span().end();
				String pos = (String) ann.get("cat");
				if (pos.equals(".")) {
					doc.annotate("S",new Span(start,posn), new FeatureSet());
					start = posn;
				}
			}
			annotator.train (doc);
			//  free up space taken by annotations on document
			doc.clearAnnotations();
		}
		posh.computeProbabilities();
	}

	/**
	 *  store the HMM associated with this tagger to file 'fileName'.
	 */

	public void store (String fileName) throws IOException {
		posh.store (new PrintWriter (new FileOutputStream (fileName)));
	}

	/**
	 *  load the HMM associated with this tagger from file 'fileName'.
	 */

	public void load (String fileName) throws IOException {
		posh.load (new BufferedReader (new FileReader (fileName)));
	}

	/**
	 *  tag 'span' of 'doc' according to the Penn Tree Bank tag set.  Words
	 *  are assigned 'constit' annotations with feature cat = a Penn tag.
	 */

	public void tagPenn (Document doc, Span span) {
		annotate (doc, span, "constit");
	}

	/**
	 *  tag 'span' of 'doc' according to the Penn Tree Bank tag set.  Words
	 *  are assigned annotations of type 'type' with feature cat = a Penn tag.
	 */

	public void annotate (Document doc, Span span, String type) {
		for (int i=0; i<posTable.length; i++)
			tagTable[i] = new String[] {type, "cat", posTable[i], posTable[i]};

		annotator = new HMMannotator(posh);
		annotator.setTagTable (tagTable);
		annotator.setBItag (false);
		annotator.setTrace (trace);

		annotator.annotateSpan (doc, span);
	}

	/**
	 *  compare the 'constit' tags of Documents 'doc' and 'key', and report (to
	 *  System.out) the agreement rate.
	 */

    public void score (Document doc, Document key) {
    	// score POS
		scorer = new SGMLScorer(doc, key);
        scorer.match("constit");
        System.out.println ("Constit tags in response:  " + scorer.numOfTagsInDoc1);
        System.out.println ("Constit tags in key:       " + scorer.numOfTagsInDoc2);
        System.out.println ("Matching constit tags:  " + scorer.numOfMatchingTags);
        System.out.println ("Matching POS     tags:  " + scorer.numOfMatchingAttrs);
        System.out.println ("Accuracy:  " +
        	(float) scorer.numOfMatchingAttrs / scorer.numOfMatchingTags);
	}

	/**
	 *  tag 'span' of 'doc' according to the Jet part of speech set.  Words
	 *  are first assigned 'tagger' annotations with feature cat = a Penn tag.
	 *  Then these are mapped to Jet tags, and 'constit' annotations are added
	 *  with cat = a Jet part-of-speech tag.
	 */

	public void tagJet (Document doc, Span span) {
		annotate (doc, span, "tagger");
		Retagger.mapConstit(doc, span);
	}

	/**
	 *  prune existing 'constit' annotations on 'span' of 'doc' using information
	 *  from a part-of-speech tagger.  Words are assumed, on entry, to have
	 *  multiple 'constit' annotations from dictionary look-up, reflecting the
	 *  POS ambiguity of the words;  this ambiguity will be reduced using a
	 *  tagger.  Words are first assigned 'tagger' annotations with feature
	 *  cat = a Penn tag.  This information is then used to remove 'constit'
	 *  annotations not consistent with the Penn tag.
	 */

	public void prune (Document doc, Span span) {
		annotate (doc, span, "tagger");
		Retagger.pruneConstit(doc, span);
	}

}
