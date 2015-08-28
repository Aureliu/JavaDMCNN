// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import Jet.Tipster.*;
import java.util.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 *  A node in the graph representation of a pattern set, representing the
 *  end of a path, associated with a set of actions to be performed if that
 *  node is reached in pattern matching.
 */

public class FinalPatternNode extends PatternNode {

  public Vector actions;
  public String patternName;

  /**
   *  Creates a FinalPatternNode with identifier <I>i</I> and set of actions
   *  <I>acts</I>.
   */

  public FinalPatternNode(Id id, String patternName, Vector actions) {
    this.id = id;
    this.patternName = patternName;
    this.actions = actions;
    }

  /**
   *  Method invoked when this node is reached during pattern matching;
   *  records the actions to be performed.  If this turns out to be the
   *  best (longest) pattern match, these actions will subsequently be
   *  performed.
   */

  public void eval (Document doc, int posn, HashMap bindings,
                    PatternApplication patap) {
    if (posn > PatternSet.limit) return;	// added 6 Sep 03
    patap.recordMatch(posn, patternName, bindings, actions);
  }

  /**
   *  Creates a printable representation of the node, consisting of "-->"
   *  followed by representations of the associated actions.
   */

  public String toString() {
    String stg = "--> ";
    for (int i = 0; i < actions.size(); i++) {
      if (i>0) stg+= ", ";
      stg += actions.get(i).toString();
      }
    return stg;
  }

  public void toTree(DefaultMutableTreeNode parent) {
    DefaultMutableTreeNode child;
    for (int i = 0; i < actions.size(); i++) {
      child = new DefaultMutableTreeNode("--> " + actions.get(i).toString());
      parent.add(child);
    }
  }
}
