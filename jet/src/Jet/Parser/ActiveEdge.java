// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2001
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

import Jet.Tipster.Annotation;
import java.util.Vector;

/**
 *  the representation of an active edge (incompletely matched production)
 *  as used by the top-down active chart parser.
 */

public class ActiveEdge extends Edge {

  /**
   *  the right-hand side of the production being matched.
   *  The first children.length elements of this production have already
   *  been matched.
   */

  Vector rhs;

  public ActiveEdge(Object category, Vector rhs, ParseTreeNode[] children,
                    int start, int end) {
    this.category = category;
    this.rhs = rhs;
    this.children = children;
    this.start = start;
    this.end = end;
  }

  /**
   *  returns the next unmatched element in the production.  I.e.,
   *  if the edge is  A -> B . C D, <B>needs</B> returns C.
   */

  public String needs () {
    int next = children.length;
    Object needed = rhs.elementAt(next);
    return (String) needed;
  }

  /**
   *  returns a printable form of the edge, in the form A -> B . C D.
   *  Here A is the symbol being expanded, B C D constitutes the right-hand
   *  side of the production, and so far symbol B has been matched.
   */

  public String toString () {
    StringBuffer stg = new StringBuffer (category.toString() + "->");
    for (int i=0; i<children.length; i++) {
      stg.append(rhs.elementAt(i).toString());
      stg.append(" ");
    }
    stg.append(". ");
    for (int i=children.length; i<rhs.size(); i++) {
      stg.append(rhs.elementAt(i).toString());
      stg.append(" ");
    }
    return stg.toString();
  }
}
