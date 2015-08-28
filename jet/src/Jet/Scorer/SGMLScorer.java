// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.03
//Description:  A Java-based Information Extraction Tool

package Jet.Scorer;

import Jet.Tipster.*;
import Jet.Lisp.FeatureSet;
import java.util.*;

/**
 *  The <CODE>SGMLScorer</CODE> class analyzes the agreement of two Documents
 *  according to one type of their annotations. More specifically, it compares
 *  an automatically generated response Document with a key Document, reporting
 *  the recall and precision of the response file relative to the key file.
 *  It also highlights the annotations which differ in the two files.
 */

public class SGMLScorer {

  public Document doc1;
  public Document doc2;
  public Vector mismatch1 = new Vector(); // annotations in doc1 with no match in doc2
  public Vector mismatch2 = new Vector(); // annotations in doc2 with no match in doc1
  public int numOfTagsInDoc1 = 0;
  public int numOfTagsInDoc2 = 0;
  public int numOfMatchingTags = 0;
  public int numOfMatchingAttrs = 0;
  public int totalTagsInDoc1 = 0;
  public int totalTagsInDoc2 = 0;
  public int totalMatchingTags = 0;
  public int totalMatchingAttrs = 0;
  String lastTypeMatched = "";

  /**
   * Construct an <CODE>SGMLScorer</CODE> to compare two Documents.
   * @param doc1 SGML file 1, or the key file
   * @param doc2 SGML file 2, or the response file
   */
  public SGMLScorer(Document doc1, Document doc2) {
    this.doc1 = doc1;
    this.doc2 = doc2;
  }

 /**
   *  Compares documents <CODE>doc1</CODE> and <CODE>doc2</CODE> with respect to
   *  annotations of type <CODE>annType</CODE>.
   */

  public void match (String annType) {
  	  match (annType, annType);
  }

  /**
   *  Compares annotations of type <CODE>annType1</CODE> of document <CODE>doc1</CODE>
   *  with annotations of type <CODE>annType2</CODE> of document <CODE>doc2</CODE>.
   */

  public void match(String annType1, String annType2) {
  	match (annType1, annType2, null);
  }

  public void match (String annType1, String annType2, Span span) {

	  numOfTagsInDoc1 = 0;
	  numOfTagsInDoc2 = 0;
	  numOfMatchingTags = 0;
	  numOfMatchingAttrs = 0;
	  lastTypeMatched = annType2;

	  Annotation currentAnn1 = null;
	  Annotation currentAnn2 = null;
	  Vector anns1 = doc1.annotationsOfType(annType1, span);
	  HashMap anns2 = annotationMap(doc2.annotationsOfType(annType2, span), doc2);
	  HashMap mismatchMap1 = annotationMap(doc1.annotationsOfType(annType1, span), doc1);
	  HashMap mismatchMap2 = annotationMap(doc2.annotationsOfType(annType2, span), doc2);
	  int size1 = 0;
	  if (anns1 != null) {
	  	size1 = anns1.size();
	  	numOfTagsInDoc1 = size1;
	  }
	  int size2 = 0;
	  if (anns2 != null) {
	  	size2 = anns2.size();
	  	numOfTagsInDoc2 = size2;
	  }
	  if (anns1 != null && anns2 != null) {
		  for (int i=0; i<size1; i++) {
		  	currentAnn1 = (Annotation) anns1.get(i);
		  	Long key = annotationKey(currentAnn1, doc1);
		  	currentAnn2 = (Annotation) anns2.get(key);
		  	if (currentAnn2 != null) {
		        numOfMatchingTags++;
		        Object type1 = currentAnn1.get("TYPE");
		        Object type2 = currentAnn2.get("TYPE");
		        Object cat1  = currentAnn1.get("CAT");
		        Object cat2  = currentAnn2.get("CAT");
		        if ((type1 == null ? type2 == null :
		        	                 type1.equals(type2)) &&
		            (cat1  == null ? cat2 == null :
		                             cat1.equals(cat2))) {
		            numOfMatchingAttrs++;
		            mismatchMap1.remove(key);
		            mismatchMap2.remove(key);
		        }
		        anns2.remove(key);
		        size2--;
		    }
		  }
	  }
	  if (mismatchMap1 != null)
	  	mismatch1.addAll(mismatchMap1.values());
	  // eliminate optional tags in key (with STATUS="OPT")
	  if (mismatchMap2 != null) {
	  	Iterator it = mismatchMap2.values().iterator();
	  	while (it.hasNext()) {
	  		Annotation a = (Annotation) it.next();
	  		Object status = a.get("STATUS");
	  		if (status != null && status.equals("OPT"))
	  			numOfTagsInDoc2--;
	  		else
	  			mismatch2.add(a);
	  	}
	  }

	  // mismatch2.addAll(mismatchMap2.values());

      totalTagsInDoc1 += numOfTagsInDoc1;
  	  totalTagsInDoc2 += numOfTagsInDoc2;
  	  totalMatchingTags += numOfMatchingTags;
  	  totalMatchingAttrs += numOfMatchingAttrs;
  }

  public String report () {
  	return "For " + lastTypeMatched + ", " +
  	       "# of matching tags = "        + numOfMatchingTags +
  	       "    # of tags in response = " + numOfTagsInDoc1 +
  	       "    # of tags in key = "      + numOfTagsInDoc2 +
  	       "    # with matching attributes = " + numOfMatchingAttrs;
  }

  private HashMap annotationMap (Vector annotationVector, Document doc) {
  	if (annotationVector == null) return null;
  	HashMap map = new HashMap(annotationVector.size());
  	for (int i=0; i<annotationVector.size(); i++) {
  		Annotation ann = (Annotation) annotationVector.get(i);
  		Long key = annotationKey(ann, doc);
  		if (map.containsKey(key)) System.out.println ("Duplicate annotation " + ann);
  		map.put(key,ann);
  	}
  	return map;
  }

  // ignorePeriods:  if true, final periods in names are not counted in matches
  boolean ignorePeriods = false;

  private Long annotationKey (Annotation ann, Document doc) {
  	long start = ann.span().start();
  	long end = ann.span().end();
  	if (ignorePeriods) {
	  	int i = ann.span().endNoWS(doc);
	  	if (i > 1 && doc.charAt(i-1) == '.')
	  		end = i - 1;
	  }
  	return new Long (start * 1000000000 + end);
  }
}

