// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

import java.util.Vector;

/**
 *  a production of the context-free grammar.
 */

public class Production {

  String lhs;
  Vector rhs;

  /**
   *  creates a new Production with the specified left hand side and
   *  right hand side.
   */

  public Production(String lhs, Vector rhs) {
    this.lhs = lhs;
    this.rhs = rhs;
  }

  /**
   *  the left-hand side (defined symbol) of the production.
   */

  public String lhs() {
    return lhs;
  }

  /**
   *  the right-hand size of the production:  a vector of Strings (grammar
   *  symbols) and Literals.
   */

  public Vector rhs() {
    return rhs;
  }
}
