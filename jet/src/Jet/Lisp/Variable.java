// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Lisp;

/**
 *  representation of a variable, as used in patterns.
 */

public class Variable {

  public String name;

  public Variable (String stg) {
    name = stg;
  }

  public String toString () {
    return "?" + name;
  }
}
