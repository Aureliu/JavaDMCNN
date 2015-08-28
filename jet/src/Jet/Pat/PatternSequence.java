// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.Vector;

/**
 *  a sequence of pattern elements which are to be matched in succession,
 *  to successive portions of a document.  Represented in a pattern file
 *  by listing the elements in sequence, <BR>
 *  a  b  c  d  e
 */

public class PatternSequence extends PatternElement {

  PatternElement elements[];

  /**
   *  creates a PatternSequence from an array of PatternElements
   */

  public PatternSequence(PatternElement elems[]) {
    elements = elems;
  }

  /**
   *  creates a PatternSequence from a Vector of PatternElements
   */

  public PatternSequence (Vector elems) {
    elements = (PatternElement[]) elems.toArray(new PatternElement[0]);
  }

  /**
   *  returns a printable representation of the PatternSequence, consisting
   *  of the constituent PatternElements, separated by spaces.
   */

  public String toString() {
    String stg = "";
    for (int i = 0; i < elements.length; i++) {
      if (i>0) stg += " ";
      stg += elements[i].toString();
    }
    return stg;
  }

  /**
   *  converts the PatternSequence to a graph representation.  A
   *  PatternSequence with <I>n</I> elements is converted into a
   *  PatternGraph with <I>n</I>-1 InternalPatternNodes: <BR>
   *  a  b  c  d  <BR>
   *  becomes <BR>
   *  --a--> O --b--> O --c--> O --d-->  <BR>
   *  where 'O' is an internal pattern node.
   */

  public PatternGraph toGraph(Id id) {
    PatternGraph elementGraph[] = new PatternGraph[elements.length+1];
    for (int i=0; i < elements.length; i++) {
      elementGraph[i] = elements[i].toGraph(id);
      if (i > 0) {
        InternalPatternNode node =
            new InternalPatternNode(new Id(id.value++), elementGraph[i].inEdgeArray());
        elementGraph[i-1].setOutEdges(node);
      }
    }
    return new PatternGraph (elementGraph[0].inEdges,
                             elementGraph[elements.length-1].outEdges);
  }
}
