// -*- tab-width: 4 -*-
//Title:        JET
//Version:
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

/**
 *  a <I>reduce</I> goal for top-down parsing.
 */

public class Reduce {

  public String category;
  public int numberOfChildren;

  /**
   *  creates a Reduce goal.  If reached on the goal stack, it will
   *  reduce <I>numChildren</I> nodes to a constituent of category <I>cat</I>.
   */

  public Reduce(String cat, int numChildren) {
    category = cat;
    numberOfChildren = numChildren;
  }
}
