// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.02
//Copyright:    Copyright (c) 2001
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.io.*;
import java.util.*;
import Jet.Lisp.*;
import Jet.Tipster.*;

/**
 *  a sequence of strings and variables, used as the argument to the
 *  "print" and "write" actions.
 */

public class StringExpression {

  Vector elements;

  /**
   *  creates a new StringExpression from the input read by StreamTokenizer
   *  <I>tok</I>, which should consist of a sequence of strings and
   *  variable names, separated by "+".
   */

  public StringExpression(StreamTokenizer tok)
         throws IOException, PatternSyntaxError {
    elements = new Vector();
    while (true) {
      if (tok.ttype == '"') {
        String stg = tok.sval;
        elements.addElement(stg);
      } else if (tok.ttype == StreamTokenizer.TT_WORD &&
                 Character.isUpperCase(tok.sval.charAt(0))) {
        String variable = tok.sval;
        elements.addElement(new Variable(variable));
      } else {
        throw new PatternSyntaxError ("invalid print/write expression");
      }
      if (tok.nextToken() != '+') return;
      tok.nextToken();
    }
  }

  /**
   *  evaluates the StringExpression at the time when print or write
   *  action is performed.  Returns the concatenation of the elements
   *  of the StringExpression.  The value of each variable element should
   *  be a Span or Annotation, and is interpreted as the text subsumed by
   *  the Span or Annotation.
   */

  public String evaluate (Document doc, PatternApplication patap) {
    StringBuffer sb = new StringBuffer();
    for (int i=0; i<elements.size(); i++) {
      Object element = elements.elementAt(i);
      if (element instanceof String) {
        sb.append((String) element);
      } else if (element instanceof Variable) {
        Variable var = (Variable) element;
        HashMap bindings = patap.bestBindings;
        Object value = bindings.get(var.name);
        if (value instanceof Span) {
          Span span = (Span) value;
          sb.append(cleanWhitespace(doc.text(span)));
        } else if (value instanceof Annotation) {
          Annotation annotation = (Annotation) value;
          sb.append(cleanWhitespace(doc.text(annotation)));
        } else {
          sb.append(" ? ");
        }
      } else {
        throw new Error ("invalid element in StringExpression");
      }
    }
    return sb.toString();
  }

  /**
   *  produces a printable form of the StringExpression, consisting of
   *  the elements of the expression, separated by "+".
   */

  public String toString () {
    StringBuffer sb = new StringBuffer();
    for (int i=0; i<elements.size(); i++) {
      if (i>0) sb.append(" + ");
      Object element = elements.elementAt(i);
      if (element instanceof String) {
        sb.append("\"" + (String)element + "\"");
      } else if (element instanceof Variable) {
        Variable var = (Variable) element;
        sb.append(var.toString());
      } else {
        throw new Error ("invalid element in StringExpression");
      }
    }
    return sb.toString();
  }

  private String cleanWhitespace (String in) {
  	StringBuffer sb = new StringBuffer(in);
  	for (int i=0; i<sb.length(); i++)
  		if (Character.isWhitespace(sb.charAt(i)))
  			sb.setCharAt(i,' ');
  	return sb.toString();
  }
}
