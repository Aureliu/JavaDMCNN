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
 *  a node in the graph representation of a pattern set.
 */

public abstract class PatternNode {
  public Id id;
  private boolean visited = false;

  public abstract void eval(Document doc, int posn, HashMap bindings,
                            PatternApplication patap);

  public abstract void toTree(DefaultMutableTreeNode parent);

  public void visit() {
    visited = true;
  }

  public boolean visited() {
    return visited;
  }
}
