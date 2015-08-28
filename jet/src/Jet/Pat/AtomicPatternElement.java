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
 *  abstract class for all PatternElements which do not contain
 *  embedded references to other PatternElements.  AtomicPatternElements
 *  are preserved unchanged when the set of PatternRules is transformed
 *  into a graph.
 */

public abstract class AtomicPatternElement extends PatternElement {

  public abstract void eval(Document doc, int posn, String tokenString,
        HashMap bindings, PatternApplication patap, PatternNode node);

  public PatternGraph toGraph(Id id) {
    PatternArc arc = new PatternArc (this,null);
    Vector inEdges = new Vector(1);
    Vector outEdges = new Vector(1);
    inEdges.add(arc);
    outEdges.add(arc);
    return new PatternGraph (inEdges,outEdges);
  }

}
