// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.HashMap;
import Jet.Lisp.Variable;
import Jet.Tipster.Document;


/**
 *  a pattern element, in the graph representation of a pattern, which
 *  binds a variable to the current position in the document being matched.
 *  A {@link SpanBindingPatternElement}, in the pattern and rule representation
 *  of a pattern set, is translated into a GetStartPatternElement and a
 *  {@link GetEndPatternElement} in the pattern graph representation.
 */
public class GetStartPatternElement extends AtomicPatternElement{

  Variable variable;

  /**
   *  creates a GetStartPatternElement binding variable <I>v</I>.
   */

  public GetStartPatternElement(Variable v) {
    variable = v;
  }

  public String toString () {
    return variable.toString() + ".start=* ";
  }

  public void eval (Document doc, int posn, String tokenString, HashMap bindings,
                    PatternApplication patap, PatternNode node) {
    bindings = (HashMap) bindings.clone();
    bindings.put(variable.name,new Integer(posn));
    node.eval(doc, posn, bindings, patap);
  }
}
