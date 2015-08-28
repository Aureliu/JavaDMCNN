// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.io.*;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Console;
import java.util.HashMap;
import java.util.Vector;

/**
 *  the action (in a when statement) for creating a new
 *  annotation on a Document.   If the spanVariable
 *  is null, the new annotation will span the portion of the document
 *  matched by the pattern;  if spanVariable is a Variable bound to a
 *  Span, the new annotation will be over that Span; if it is bound to
 *  an Annotation, the new annotation will be over the same span..
 */

public class NewAnnotationAction extends Action {

  String type;
  FeatureSet features;
  Variable spanVariable;
  Variable bindingVariable;

  public NewAnnotationAction(String tp, FeatureSet fs, Variable sv) {
    type = tp;
    features = fs;
    spanVariable = sv;
  }

  /**
   *  creates a NewAnnotationAction from the input processed by StreamTokenizer
   *  <I>tok</I>, which should have the form <BR>
   *  add [type feature=value feature=value ...] <BR>
   *  or <BR>
   *  add [type feature=value feature=value ...] over spanVariable
   */

  public NewAnnotationAction (StreamTokenizer tok)
    throws IOException, PatternSyntaxError {
    if (tok.nextToken() == StreamTokenizer.TT_WORD &&
      Character.isUpperCase(tok.sval.charAt(0))) {
      bindingVariable = new Variable(tok.sval);
      if (tok.nextToken() != '=') throw new PatternSyntaxError ("= expected");
      tok.nextToken();
    }
    if (tok.ttype != '[')
      throw new PatternSyntaxError ("[ expected");
    if (tok.nextToken() != StreamTokenizer.TT_WORD)
      throw new PatternSyntaxError ("annotation type expected");
    type = tok.sval;
    features = new FeatureSet (tok, true, ']');
    if (tok.nextToken() == StreamTokenizer.TT_WORD &&
        tok.sval.equalsIgnoreCase("over")) {
      if (tok.nextToken() ==  StreamTokenizer.TT_WORD &&
          Character.isUpperCase(tok.sval.charAt(0))) {
        spanVariable = new Variable(tok.sval);
        tok.nextToken();
      } else if (tok.ttype == StreamTokenizer.TT_NUMBER &&
          tok.nval == 0) {
        spanVariable = new Variable("0");
        tok.nextToken();
      } else {
        throw new PatternSyntaxError ("variable expected after 'over'");
      }
    } else {
      spanVariable = null;
    }
  }

  /**
   *  performs the action, adding the specified Annotation.
   *  Returns the position of the end of the Annotation.
   */

  public int perform(Document doc, PatternApplication patap) {
    Span span;
    HashMap bindings = patap.bestBindings;
    // System.out.println ("bindings (for new annotation): " + bindings);
    if (spanVariable == null) {
      span =  new Span (patap.startPosition, patap.bestPosition);
    } else if (spanVariable.name.toString() == "0") {
      span = new Span (patap.startPosition,patap.startPosition);
    } else {
      Object value = bindings.get(spanVariable.name);
      if (value instanceof Span) {
        span = (Span) value;
      } else if (value instanceof Annotation) {
        span = ((Annotation) value).span();
      } else {
        System.out.println ("Value of "+ spanVariable.toString() +
                            " is not a span.or annotation");
        return -1;
      }
    }
    if (Pat.trace) Console.println ("Annotating " + doc.text(span) + " as " + type
                                    + " " + features.substitute(bindings).toSGMLString());
    hideAnnotations (doc, type, span);
    hideAnnotations (doc, "token", span);
    Annotation newAnnotation =
      new Annotation(type, span, features.substitute(bindings));
    doc.addAnnotation(newAnnotation);
    if (bindingVariable != null)
      bindings.put(bindingVariable.name,newAnnotation);
    return span.end();
  }

  /**
   *  hides (adds the 'hidden' feature) to all annotations of type <I>type</I>
   *  beginning at the starting position of span <I>span</I>.
   */

  public static void hideAnnotations (Document doc,  String type, Span span) {
  	for (int posn = span.start(); posn < span.end(); posn++) {
	    Vector annotations = doc.annotationsAt(posn, type);
	    if (annotations != null) {
	      for (int i=0; i<annotations.size(); i++) {
	        Annotation ann = (Annotation) annotations.elementAt(i);
	        ann.put("hidden","true");
	        // Console.println ("Hiding " + ann);
	      }
	    }
	}
  }

  public String toString() {
    if (spanVariable == null)
      return "add [" + type + features.toSGMLString() + "]";
    else
      return "add [" + type + features.toSGMLString() + "] over " + spanVariable.toString();
  }
}
