// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import Jet.Lisp.*;
import Jet.Tipster.*;
import java.util.HashMap;

/**
 *  a pattern element which assigns a value (a String or integer) to a
 *  pattern variable.
 */

public class AssignmentPatternElement extends AtomicPatternElement {

  Variable variable;
  Object value;

  public AssignmentPatternElement(Variable v, String val) {
    variable = v;
    value = val;
  }

  public AssignmentPatternElement (Variable v, Integer val) {
    variable = v;
    value = val;
  }

  public String toString() {
    return variable.toString() + " = " + value.toString();
  }

  public void eval (Document doc, int posn, String tokenString, HashMap bindings,
                    PatternApplication patap, PatternNode node) {
    bindings = (HashMap) bindings.clone();
    bindings.put(variable.name,value);
    node.eval(doc, posn, bindings, patap);
  }
}
