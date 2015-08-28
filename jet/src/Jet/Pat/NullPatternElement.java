// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.*;
import Jet.Tipster.*;

/**
 *  a pattern element which always succeeds.  Used in creating graphs
 *  for optional pattern elements.
 */

public class NullPatternElement extends AtomicPatternElement {

  public NullPatternElement() {
  }

  public String toString () {
    return "null";
  }

  public void eval(Document doc, int posn, String tokenString, HashMap bindings,
                   PatternApplication patap, PatternNode node) {
    node.eval(doc, posn, bindings, patap);
  }
}
