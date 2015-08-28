// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.Vector;

/**
 *  a pattern element for recording an alternation of patterns
 *  (A | B | C).
 */

public class PatternAlternation extends PatternElement {

  PatternElement options [];

  public PatternAlternation(PatternElement opts[]) {
    options = opts;
  }

  public PatternAlternation (Vector opts) {
    options = (PatternElement[]) opts.toArray(new PatternElement[0]);
  }

  public String toString() {
    String stg = "(";
    for (int i = 0; i < options.length; i++) {
      if (i > 0) stg += " | ";
      stg += options[i].toString();
    }
    stg += ")";
    return stg;
  }

  /* build a graph for the alternation:
     with  inEdges = U of inEdges of all options
           outEdges = U of outEdges of all options
  */

  public PatternGraph toGraph (Id id) {
    Vector inEdges = new Vector(), outEdges = new Vector();
    for (int i=0; i<options.length; i++) {
      PatternGraph optionGraph = options[i].toGraph(id);
      inEdges.addAll(optionGraph.inEdges);
      outEdges.addAll(optionGraph.outEdges);
    }
    return new PatternGraph (inEdges,outEdges);
  }
}
