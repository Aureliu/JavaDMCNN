// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.05
//Copyright:    Copyright (c) 2001
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.io.StreamTokenizer;
import java.io.IOException;
import java.util.HashSet;
import Jet.Lisp.*;
import Jet.Concepts.*;
import Jet.JetTest;

/**
 *  representation of a condition on a feature value, represented in the
 *  pattern language by <code>? predicate (argument)</code>.
 */

public class FeatureTest {

  String predicate;
  Object argument;
  static HashSet predicateNames = new HashSet();
  static {
    predicateNames.add("ne");
    predicateNames.add("isa");
  }

  /**
   * creates a FeatureTest with the specified predicate and argument.
   */

  public FeatureTest (String predicate, Object argument) {
    this.predicate = predicate;
    this.argument = argument;
  }

  /**
   * creates a FeatureTest from the next tokens in <i>tok</i>.  These
   * should have the form <br>
   * ? predicate ( argument ) <br>
   * where the '?' is the current token on entry, <i>argument</i> is
   * a symbol or integer, and the ')' is the current token on exit.
   */
  public FeatureTest (StreamTokenizer tok)
      throws IOException, PatternSyntaxError {
    if (tok.ttype != '?') throw new PatternSyntaxError ("? expected");
    if (tok.nextToken() != StreamTokenizer.TT_WORD)
      throw new PatternSyntaxError ("predicate name expected");
    predicate = tok.sval.intern();
    if (! predicateNames.contains(predicate))
      throw new PatternSyntaxError ("predicate name expected");
    if (tok.nextToken() != '(') throw new PatternSyntaxError ("( expected");
    if (tok.nextToken() == StreamTokenizer.TT_WORD) {
      String value = tok.sval.intern();
      if (Character.isUpperCase(value.charAt(0))) {
        throw new PatternSyntaxError ("Capitalized name (variable) not allowed");
      } else {
        argument = value;
      }
    } else if (tok.ttype == StreamTokenizer.TT_NUMBER) {
      argument = new Integer((int) tok.nval);
    } else throw new PatternSyntaxError ("symbol or integer expected");
    if (tok.nextToken() != ')') throw new PatternSyntaxError ("( expected");
  }

  /**
   * returns <b>true</b> if this FeatureTest is satisfied by <i>value</i>.
   */

  public boolean apply (Object value) {
    boolean result;
    if (predicate == "ne")
      result = (value != argument);
    else if (predicate == "isa") {
    	ConceptHierarchy ch = JetTest.conceptHierarchy;
      if (ch != null && value instanceof String && argument instanceof String) {
      	Concept concept1 = ch.getConceptByName ((String) argument);
        Concept concept2 = ch.getConceptFor ((String) value);
        result = (concept1 != null) &&
                 (concept2 != null) && ch.isaStar (concept2, concept1);
      } else result = false;
    } else // undefined predicate
      result = false;
    return result;
  }
}
