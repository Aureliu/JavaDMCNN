// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import Jet.Lisp.*;
import java.util.Vector;

/**
 *  a pattern construct which binds a variable to the span matched
 *  by a pattern element.  On a pattern file, this takes the form <BR>
 *  (pattern element):Variable
 */

public class SpanBindingPatternElement extends PatternElement {

  Variable variable;
  PatternElement element;

  /**
   *  creates a SpanBindingPatternElement.
   */

  public SpanBindingPatternElement(PatternElement pe, Variable v) {
    element = pe;
    variable = v;
  }

  /**
   *  produces a string representation of the pattern element, of the
   *  form <I>element : variable</I>
   */

  public String toString() {
    return element.toString() + " :" + variable.toString();
  }

  /**
   *  converts the pattern element to its pattern graph representation,
   *  including a separate GetStartPatternElement and GetEndPatternElement.
   */

  public PatternGraph toGraph (Id id) {
    GetStartPatternElement getStart = new GetStartPatternElement (variable);
    PatternGraph elementGraph = element.toGraph(id);
    GetEndPatternElement getEnd = new GetEndPatternElement (variable);
    PatternArc endArc = new PatternArc (getEnd, null);
    Vector newOutEdges = new Vector(1);
    newOutEdges.add(endArc);
    InternalPatternNode node2 = new InternalPatternNode (new Id(id.value++), newOutEdges);
    elementGraph.setOutEdges(node2);
    InternalPatternNode node1 = new InternalPatternNode (new Id(id.value++), elementGraph.inEdgeArray());
    PatternArc startArc = new PatternArc (getStart, node1);
    Vector newInEdges = new Vector(1);
    newInEdges.add(startArc);
    return new PatternGraph (newInEdges, newOutEdges);
  }
}
