// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.Vector;

/**
 *  an element in a pattern which stands for a reference to another pattern.
 */

public class PatternReference extends PatternElement {

  String patternName;
  PatternCollection collection;

  /**
   *  creates a reference to the pattern named <I>patternName</I> in
   *  collection <I>collection</I>.
   */

  public PatternReference(String patternName, PatternCollection collection) {
    this.patternName = patternName;
    this.collection = collection;
  }

  /**
   *  returns a printable form of the pattern reference:  the pattern name.
   */

  public String toString () {
    return patternName;
  }

  /**
   *  converts the PatternReference to a graph which can be inserted into
   *  a pattern graph.  If the referenced pattern is defined, we convert
   *  the pattern as defined.  If it is not defined, we print a message and
   *  create an arc which matches the token "*undefined*".
   */

  public PatternGraph toGraph (Id id) {
    PatternElement pe = collection.dereference(patternName);
    if (pe == null) {
    // if symbol is not defined, return arc for "*undefined*"
      System.out.println ("Reference to undefined pattern " + patternName);
      PatternArc undefArc = new PatternArc(new TokenStringPatternElement("*undefined*"),null);
      Vector newInEdges = new Vector(1);
      Vector newOutEdges = new Vector(1);
      newInEdges.add(undefArc);
      newOutEdges.add(undefArc);
      return new PatternGraph (newInEdges, newOutEdges);
    } else {
      return pe.toGraph(id);
    }
  }
}
