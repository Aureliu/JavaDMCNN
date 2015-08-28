// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import Jet.Tipster.*;
import java.util.*;

/**
 *  a pattern element which matches a specific word.  More precisely, it
 *  matches an annotation of type TOKEN which subsumes a specified string
 *  in the document.
 */

public class TokenStringPatternElement extends AtomicPatternElement{
  private String string;

  public TokenStringPatternElement(String stg) {
    string = stg.intern();
  }

  public void eval(Document doc, int posn, String tokenString, HashMap bindings,
                   PatternApplication patap, PatternNode node) {
    if (tokenString != string) return;
    /* Annotation ann = doc.tokenAt(posn);
    if (doc.text(ann).equals(string)) {
    */
    int ic = doc.tokenAt(posn).span().end();
    // while ((ic < doc.length()) && Character.isWhitespace(doc.charAt(ic))) ic++;
    node.eval(doc, ic, bindings, patap);
  }

  public String toString () {
    return "\"" + string + "\"";
  }

}
