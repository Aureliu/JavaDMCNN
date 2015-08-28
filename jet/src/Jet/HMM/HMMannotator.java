// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;

import Jet.Tipster.*;
import Jet.Lex.*;
import Jet.Lisp.*;
import Jet.Scorer.*;
import Jet.Console;
import Jet.Chunk.*;

/**
 *  HMMAnnotator provides methods for training and using HMMs with annotated
 *  Documents.
 */

public class HMMannotator {

	TokenClassifier hmm;
	// tagTable:  list of annotations to be used to train HMM
	//    annotation type | feature [or null if no feature constraint] | feature-value | tag
	String[][] tagTable;
	boolean BItag;
	boolean annotateEachToken;
	String zoneToTag;
	boolean trace;
	boolean recordMargin = false;
	boolean recordProbability = false;

	/**
	 *  create a new annotator based on HMM h.
	 */

	public HMMannotator (TokenClassifier h) {
		hmm = h;
		tagTable = new String[0][4];
		BItag = false;
		annotateEachToken = true;
		zoneToTag = "S";
		trace = false;
	}

	/**
	 *  define the tag table for the annotator -- the correspondence between the
	 *  tags associated with the states and the annotations on the documents.
	 *  The tag table is a two-dimensional array, where each row contains 4 elements: <br>
	 *    annotation-type | feature [or null if no feature constraint] | feature-value | tag <br>
	 *  This specifies that the annotation with the given annotation-type (and
	 *  feature / feature-value, if not null) matches the HMM state with tag 'tag'.
	 */

	public void setTagTable (String[][] table) {
		tagTable = table;
	}

	/**
	 *  read the tag table (the list of annotation types and features)
	 *  from file 'tagFileName'.
	 */

	public void readTagTable (String tagFileName) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(tagFileName));
      readTagTable (in);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

	/**
	 *  read the tag table (the list of annotation types and features)
	 *  from BufferedReader 'in'. Each line must be of the form <br>
	 *  annotationType HMMtag <br>
	 *  or
	 *  annotationType feature featureValue HMMtag <br>
	 *  where 'HMMtag' ties this line to a state of the HMM.  Stops at
	 *  the end-of-file or on encountering a line 'endtags'.
	 */

  public void readTagTable (BufferedReader in) {
  	try {
      String line, hmmTag;
      ArrayList tagTableList = new ArrayList();
      while ((line = in.readLine()) != null) {
        String[] tags = line.split("\\s+");
        String[] tagTableEntry = new String[4];
		    if (tags.length == 2) {
		    	tagTableEntry[0] = tags[0].intern();
		    	hmmTag = tags[1].intern();
		    	tagTableEntry[3] = hmmTag;
		    } else if (tags.length == 4) {
		    	tagTableEntry[0] = tags[0].intern();
		    	tagTableEntry[1] = tags[1].intern();
		    	tagTableEntry[2] = tags[2].intern();
		    	hmmTag = tags[3].intern();
		    	tagTableEntry[3] = hmmTag;
		    } else if (tags.length == 1 && tags[0].equals("endtags")) {
		    	break;
		    } else {
		    	System.out.println ("*** Invalid entry in tag table file: " + line);
		    	continue;
		    }
		    tagTableList.add(tagTableEntry);
      }
      tagTable = (String[][]) tagTableList.toArray(new String[0][0]);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   *  writes the tag table (the correspondence between HMM tags and
   *  annotation types and features) to PrintWriter 'pw'.
   */

  public void writeTagTable (BufferedWriter pw) {
  	try {
	  	for (int i=0; i<tagTable.length; i++) {
	  		if (tagTable[i][1] == null)
	  			pw.write (tagTable[i][0] + " " + tagTable[i][3]);
	  		else
	  			pw.write (tagTable[i][0] + " " + tagTable[i][1] + " " +
	  			            tagTable[i][2] + " " + tagTable[i][3]);
	  		pw.newLine();
	  	}
	  } catch (IOException e) {
	  	System.out.println ("Error in HMMannotator.writeTagTable: " + e);
	  	System.exit (0);
	  }
  }

  /**
   *  returns the tag table (the correspondence between HMM tags and
   *  annotation types and features).
   */

  public String[][] getTagTable () {
  	return tagTable;
  }

	/**
	 *  sets / clears the BItag flag.  If the flag is false, then the tag given by
	 *  the tag table is matched directly against the tag on the state, If the flag is
	 *  true, the tag in the tag table matches tags B-tag and I-tag on HMM states.
	 *  More precisely, if an annotation is associated with a tag X through the tag table,
	 *  and the annotation spans three tokens, the first token must match an HMM
	 *  state with tag B-X, and the remaining tokens must match HMM states with
	 *  tag I-X.
	 */

	public void setBItag (boolean flag) {
		BItag = flag;
	}

	/**
	 *  sets / clears the annotateEachToken flag, which applies only if BItag == false.
	 *  If annotateEachToken is true, then the span associated with each token
	 *  receives a separate annotation (if assigned a tag which corresponds to an
	 *  annotation), If this flag is false, then we look for the maximal sequence of
	 *  tokens which are assigned the same tag, and assign it a single annotation
	 *  (again, if the tag corresponds to an annotation).
	 */

	public void setAnnotateEachToken (boolean flag) {
		annotateEachToken = flag;
	}

	/**
	 *  sets the zones to be annotated.  For example, if zone="P", then each sequence
	 *  of tokens spanned by an annotation of type P will be processed, either to
	 *  train the HMM or to annotate the text using the HMM.
	 */

	public void setZoneToTag (String zone) {
		zoneToTag = zone;
	}

	/**
	 *  turn on / off the trace
	 */

	public void setTrace (boolean trace) {
		this.trace = trace;
	}

	/**
	 *  turn on/off the feature that records the margin associated with an
	 *  annotation as a feature 'margin' on the annotation.  The margin is the
	 *  difference between the log probability of the analysis including this
	 *  annotation and the log probability of the most likely analysis
	 *  excluding this annotation.
	 */

	public void setRecordMargin (boolean recordMargin) {
		this.recordMargin = recordMargin;
	}

	/**
	 *  turn on/off the feature that records the (log) probability of
	 *  the assignment of tags to a span of text.  It is recorded as an
	 *  annotation <b>HMMtags</b> with a feature <b>prob</b>.
	 */

	public void setRecordProb (boolean recordProbability) {
		this.recordProbability = recordProbability;
	}

	// return tag associated with an annotation, or null

	private String tagForAnnotation (Annotation ann) {
		for (int i=0; i<tagTable.length; i++) {
			String[] tagEntry = tagTable[i];
			if (ann.type().equals(tagEntry[0]) &&
				(tagEntry[1] == null || ann.get(tagEntry[1]).equals(tagEntry[2]))) {
				return tagEntry[3];
			}
		}
		return null;
	}
	
	/**
	 *  limit on length (in tokens) of an annotation;  longer annotations are
	 *  not added to the document.
	 */
	final int LONGEST_ANNOTATION_SPAN = 10;

	// assign to 'span' the annotation associated with tag 'tag'.

	private void annotateForTag (Document doc, String tag,
	                             Annotation[] tokens, int first, int last) {
	  if (last - first + 1 > LONGEST_ANNOTATION_SPAN)
	    return;
	  int start = tokens[first].start();
	  int end = tokens[last].end();
	  Span span = new Span(start, end);
		for (int i=0; i<tagTable.length; i++) {
			String[] tagEntry = tagTable[i];
			if (tag.equals(tagEntry[3])) {
				double margin = 0.;
				if (recordMargin) {
					margin = hmm.getLocalMargin (doc, tokens, tag, first, last);
					// if (margin < 8) return;
				}
				FeatureSet fs = (tagEntry[1]==null) ?
				                new FeatureSet() : new FeatureSet(tagEntry[1], tagEntry[2]);
				Annotation ann = new Annotation (tagEntry[0], span, fs);
				doc.addAnnotation(ann);
				if (recordMargin) {
					ann.put("margin", new Integer((int)margin));
				}
				if (trace)
					Console.println ("Annotate " + doc.normalizedText(span)
					                 + " == " + ann.toSGMLString());
				return;
			}
		}
		return;
	}

	/**
	 *  use the annotations on Document 'doc' to train the HMM.  Assumes the
	 *  document has 'zoneToTag' annotations, and trains on each zone
	 *  separately.
	 */

	public void train (Document doc) {
		// zone document
		Vector textSegments = doc.annotationsOfType (zoneToTag);
		if (textSegments == null) {
			System.out.println ("HMMAnnotate.train:  no " + zoneToTag +
			                    " annotations in document.");
			return;
		}
      	Iterator it = textSegments.iterator ();
      	while (it.hasNext ()) {
	        Annotation para = (Annotation)it.next ();
	        Span textSpan = para.span ();
	        Tokenizer.tokenize (doc, textSpan);
	        trainOnSpan (doc, textSpan);
	     }
	 }

	 /**
	  *  use the annotations on Span 'span' of Document 'doc' to train the HMM.
	  */

	 public void trainOnSpan (Document doc, Span textSpan) {
	 	String tag, tokenTag;
		String continuationTag = "other";
		int markupEnd = 0;
		ArrayList tokens = new ArrayList();
		ArrayList tags = new ArrayList();
		int posn = textSpan.start();
		int end = textSpan.end();  // end of zone
		// skip whitespace at beginning of textSpan
		posn = Tokenizer.skipWSX(doc, posn, end);
		continuationTag = "other";
		while (posn < end) {
			Annotation token = doc.tokenAt(posn);
	        if (token == null) break;
	        // is there a xx tag at this position?
	        Vector anns = doc.annotationsAt(posn);
	        tag = null;
	        Annotation ann = null;
	        for (int i=0; i<anns.size(); i++) {
	        	ann = (Annotation) anns.get(i);
	        	if((tag = tagForAnnotation(ann)) != null) break;
	        }
	        if (tag != null) {
	        	// 1. check no active markup (markupEnd == 0)
	        	if (markupEnd == 0) {
		        	// 2. set tag = start-tag and continuation tag
		        	//   B / I tags
		        	if (BItag) {
			        	tokenTag = ("B-" + tag).intern();
			        	continuationTag = ("I-" + tag).intern();
			        } else {
			        	tokenTag = tag;
			        	continuationTag = tag;
			        }
		        	// 3. record markupEnd
		        	markupEnd = ann.span().end();
		        } else {
		        	System.out.println ("Nested tag " + tag + " ignored.");
		        	System.out.println ("(tag from annotation " + ann + ")");
		        	tokenTag = continuationTag;
		        }
	    	} else {
	    		tokenTag = continuationTag;
	    	}
	        /* String[] typetoken = new String[3];
	        typetoken[0] = doc.text(token).trim();  // without whitespace
	        typetoken[1] = tokenTag;
	        typetoken[2] = (String) token.get("tokenType");
	        tt.add(typetoken); */
	        tokens.add(token);
	        tags.add(tokenTag);
	        posn = token.span().end();
	        // if this token matches end point,
	        //     clear end point and set continuation tag = other
	        if (markupEnd != 0 && posn > markupEnd) {
	        	System.out.println ("Annotation does not end at token boundary");
	        	System.out.println ("(annotation ends at " + markupEnd +
	        	                    ", token boundary is " + posn);
	        }
	        if (posn >= markupEnd) {
	        	markupEnd = 0;
	        	continuationTag = "other";
	        }
		}
		if (markupEnd != 0) {
			System.out.println ("Annotation extends past text [sentence] boundary");
			System.out.println ("(annotation ends at " + markupEnd + ")");
		}
	    // String[][] z = new String[tt.size()][];
		// for (int i=0; i<tt.size(); i++) z[i] = (String[]) tt.get(i);
		int size = tokens.size();
		Annotation[] tokenArray = (Annotation[]) tokens.toArray (new Annotation[size]);
		String[] tagArray = (String[]) tags.toArray (new String[size]);
		hmm.train(doc, tokenArray, tagArray);
	}


	/**
	 *  use the annotations on all documents in DocumentCollection 'col'
	 *  to train HMM 'h'.
	 */

	public void train (DocumentCollection col) {
		col.open();
		for (int i=0; i<col.size(); i++) {
			ExternalDocument doc = col.get(i);
			doc.open();
			System.out.println ("Training from " + doc.fileName());
			train (doc);
		}
	}

	/**
	 *  use the HMM to add annotations to Document 'doc'.  The Viterbi algorithm is used
	 *  to find the most likely state sequence for each token sequence;  the tags on the
	 *  resulting states are used to generate annotations (based on the tagTable).
	 */

	public void annotate (Document doc) {
		Vector textSegments = doc.annotationsOfType (zoneToTag);
		if (textSegments == null)
			System.out.println ("HMMAnnotate.annotate:  no " + zoneToTag +
			                    " annotations in document.");
		else {
			Iterator it = textSegments.iterator ();
			while (it.hasNext ()) {
				Annotation para = (Annotation)it.next ();
				Span textSpan = para.span ();
				annotateSpan (doc, textSpan);
			}
		}
	}
	/**
	 *  use the HMM to add N-best annotations to Document 'doc'.  The n most
	 *  likely state sequences are computed for each token sequence;  the tags on the
	 *  resulting states are used to generate annotations (based on the tagTable).
	 *  The set of annotations associated with each sequence is tagged as a
	 *  separate hypothesis with <b>hypo</b> tag <CODE>hypId</CODE> followed by
	 *  the hypothesis number.
	 */

	public void annotateNbest (Document doc, int n, String hypId) {
		Vector textSegments = doc.annotationsOfType (zoneToTag);
		if (textSegments == null)
			System.out.println ("HMMAnnotate.annotate:  no " + zoneToTag +
			                    " annotations in document.");
		else {
			Iterator it = textSegments.iterator ();
			while (it.hasNext ()) {
				Annotation para = (Annotation)it.next ();
				Span textSpan = para.span ();
				annotateSpanNbest (doc, textSpan, n, hypId);
			}
		}
	}

	/**
     *  use the HMM to add annotations to Span 'textSpan' of Document 'doc'.
     *  The Viterbi algorithm is used to find the most likely state sequence
     *  for each token sequence;  the tags on the resulting states
     *   are used to generate annotations (based on the tagTable).
     */

	public void annotateSpan (Document doc, Span textSpan) {
		// gather tokens in textSpan;  if none, return
		Annotation[] tokens = Tokenizer.gatherTokens(doc, textSpan);
		if (tokens.length == 0) return;
		// set tags using HMM
		String[] tags = hmm.viterbi(doc, tokens);
		// if Viterbi decoder found no path through HMM, return
		if (tags == null) return;
		tagsToAnnotations (doc, tokens, tags);
	}

	/**
	 *  use the HMM to add annotations to Span 'textSpan' of Document 'doc'.
	 *  The <CODE>n</CODE> most likely state sequences are computed
	 *  for each token sequence.  The tags on the resulting states
	 *  are used to generate annotations (based on the tagTable).  The annotations
	 *  for the <i>i</i>th most likely state sequence are marked as a hypothesis
	 *  with <b>hypo</b> feature <CODE>hypId</CODE>-<i>i</i>.
	 *
	 *  @return an ArrayList of the hypothesis identifiers
	 */

	public ArrayList annotateSpanNbest (Document doc, Span textSpan, int n, String hypId) {
		ArrayList hypotheses = new ArrayList();
		// gather tokens in textSpan;  if none, return
		Annotation[] tokens = Tokenizer.gatherTokens(doc, textSpan);
		if (tokens.length == 0) return hypotheses;
		// set tags using HMM
		// hmm.recordMargin();                        // <<< for margin-conditioned Nbest
		String[] tags = hmm.viterbi(doc, tokens);
		// double margin = hmm.getMargin();           // <<< for margin-conditioned Nbest
		// System.out.println ("Margin = " + margin); // <<< for margin-conditioned Nbest
		// if (margin > 10.0) n = 1;                  // <<< for margin-conditioned Nbest
		// if Viterbi decoder found no path through HMM, return empty set
		if (tags == null) return hypotheses;
		String hypothesis = hypId + "-0";
		doc.setCurrentHypothesis(hypothesis);
		tagsToAnnotations (doc, tokens, tags);
		hypotheses.add(hypothesis);
		if (recordProbability) {
			doc.annotate("HMMtags", textSpan,
			             new FeatureSet("prob", new Integer((int)hmm.getPathProbability())));
		}
		for (int i=1; i<n; i++) {
			// set tags using HMM
			tags = hmm.nextBest();
			// if decoder found no more paths through HMM, return
			if (tags == null) break;
			hypothesis = hypId + "-" + i;
			doc.setCurrentHypothesis(hypothesis);
			tagsToAnnotations (doc, tokens, tags);
			hypotheses.add(hypothesis);
			if (recordProbability) {
				doc.annotate("HMMtags", textSpan,
				             new FeatureSet("prob", new Integer((int)hmm.getPathProbability())));
			}
		}
		doc.setCurrentHypothesis(null);
		return hypotheses;
	}

	private void tagsToAnnotations (Document doc, Annotation[] tokens, String[] tags) {
		// convert tags to annotations
		if (BItag) {
			// for B/I tagging
			int start = -1;
			String xtag = "";
			for (int i=0; i<tokens.length; i++) {
				Annotation tokenAnn = tokens[i];
				String tag = tags[i];
				if (tag.length() > 2 && !tag.substring(0,2).equals("I-") && start>=0) {
					annotateForTag(doc, xtag, tokens, start, i-1);
					start = -1;
				}
				if (tag.length() > 2 && tag.substring(0,2).equals("B-")) {
					start = i;
					xtag = tag.substring(2);
				}
			}
		} else if (annotateEachToken) {
			// for simple tagging
			for (int i=0; i<tokens.length; i++) {
				annotateForTag(doc, tags[i], tokens, i, i);
			}
		} else {
			Annotation tokenAnn = tokens[0];
			int first = 0;
			String tag = tags[0];
			for (int i=1; i<tokens.length; i++) {
				tokenAnn = tokens[i];
				if (!tags[i].equals(tag)) {
					annotateForTag(doc, tag, tokens, first, i-1);
					tag = tags[i];
					first = i;
				}
			}
			annotateForTag(doc, tag, tokens, first, tokens.length-1);
		}
	}
}
