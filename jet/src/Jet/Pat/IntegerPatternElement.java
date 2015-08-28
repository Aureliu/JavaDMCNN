// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import Jet.Lisp.*;
import Jet.Tipster.*;
import java.util.*;

/**
 *  a pattern element which matches an integer token.  The minimum and
 *  maximum allowed values of the integer may be specified.
 */

public class IntegerPatternElement extends AtomicPatternElement {

  Integer min, max;
  Variable intvalueVariable;

  public IntegerPatternElement(FeatureSet fs) {
    min = (Integer) fs.get("min");
    max = (Integer) fs.get("max");
    intvalueVariable = (Variable) fs.get("intvalue");
  }

  public void eval(Document doc, int posn, String tokenString, HashMap bindings,
                   PatternApplication patap, PatternNode node) {
    Annotation token = doc.tokenAt(posn);
    if (token == null) return;
    // String text = doc.text(token);
    Integer value = (Integer) token.get("intvalue");
    if (value == null) return;
    if (min != null && value.intValue() < min.intValue()) return;
    if (max != null && value.intValue() > max.intValue()) return;
    int ic = token.span().end();
    if (intvalueVariable != null) {
      bindings = (HashMap) bindings.clone();
      bindings.put(intvalueVariable.name,value);
    }
    node.eval(doc, ic, bindings, patap);
  }

  public String toString () {
    String result = "[integer";
    if (min != null) result += "min = " + min;
    if (max != null) result += "max = " + max;
    result += "]";
    return result;
  }
}
