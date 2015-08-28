// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.Vector;

/**
 *  internal representation of a <B>when</B> statement in a pattern file,
 *  indicating that when pattern <I>patternName</I> is matched, <I>actions</I>
 *  should be performed.
 */

public class PatternRule {

  String patternName;
  Vector actions;

  public PatternRule(String patName, Vector acts) {
    patternName = patName;
    actions = acts;
  }

  public String patternName() {
    return patternName;
  }

  public Vector actions () {
    return actions;
  }
}
