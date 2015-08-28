// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000, 2001
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.Vector;

/**
 *  a pattern element for representing an optional or repeated pattern,
 *  A? (zero or one instance of A), A* (zero or more instances of A), or A+
 *  (one or more instances of A).
 */

public class PatternRepetition extends PatternElement {

  PatternElement element;
  char repetition;

  public PatternRepetition(PatternElement pe, char rep) {
    element = pe;
    repetition = rep;
  }

  public String toString() {
    return "(" + element.toString() + ")" + repetition;
  }

  /**
   *  converts a PatternRepetition to its graph representation.
   */

  public PatternGraph toGraph (Id id) {
    InternalPatternNode node;
    PatternArc nullArc;
    Vector newInEdges, newOutEdges;
    // create graph representation of 'element'
    PatternGraph elementGraph = element.toGraph(id);
    Vector inEdges = new Vector(elementGraph.inEdges);
    Vector outEdges = new Vector(elementGraph.outEdges);
    switch (repetition) {
      case '?':
        // add null arc in parallel with graph
        nullArc = new PatternArc(new NullPatternElement(),null);
        inEdges.add(nullArc);
        outEdges.add(nullArc);
        return new PatternGraph (inEdges, outEdges);
      case '*':
        PatternArc nullArc2 = new PatternArc(new NullPatternElement(),null);
        inEdges.add(nullArc2);
        node = new InternalPatternNode (new Id(id.value++), inEdges);
        elementGraph.setOutEdges(node);
        PatternArc nullArc1 = new PatternArc(new NullPatternElement(),node);
        newInEdges = new Vector(1);
        newOutEdges = new Vector(1);
        newInEdges.add(nullArc1);
        newOutEdges.add(nullArc2);
        return new PatternGraph (newInEdges, newOutEdges);
      case '+':
        nullArc = new PatternArc(new NullPatternElement(),null);
        inEdges.add(nullArc);
        node = new InternalPatternNode (new Id(id.value++), inEdges);
        elementGraph.setOutEdges(node);
        newOutEdges = new Vector(1);
        newOutEdges.add(nullArc);
        // make second copy of graph for 'element'
        PatternGraph elementGraph2 = element.toGraph(id);
        elementGraph2.setOutEdges(node);
        return new PatternGraph (elementGraph2.inEdges, newOutEdges);
      default:
        System.out.println ("Invalid repetition character " + repetition);
        return elementGraph;
    }
  }
}
