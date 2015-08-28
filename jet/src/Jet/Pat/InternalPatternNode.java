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
 *  a non-final node in the graph representation of a pattern set
 *  (a node with outgoing arcs).
 */

public class InternalPatternNode extends PatternNode {

  /**
   *  the arcs leaving this node.
   */

  public PatternArc arcs[];

  public InternalPatternNode(Id i, PatternArc a[]) {
    id = i;
    arcs = a;
  }

  public InternalPatternNode(Id i,Vector a) {
    id = i;
    arcs = (PatternArc []) a.toArray (new PatternArc[0]);
  }

  public void eval(Document doc, int posn, HashMap bindings,
                   PatternApplication patap) {
    if (posn > PatternSet.limit) return;	// added 6 Sep 03
    Annotation token = doc.tokenAt(posn);
    String tokenString = (token==null) ? null : doc.text(token).trim().intern();
    for  (int i = 0; i < arcs.length; i++)
      arcs[i].eval(doc, posn, tokenString, bindings, patap);
  }

  public String toString () {
    String result = "";
    if (arcs.length == 1) {
      result += arcs[0].pe.toString() + " " + arcs[0].target.id.value;
      if (!arcs[0].target.visited()) {
        arcs[0].target.visit();
        result += " " + arcs[0].target.toString();
      }
    }
    else if (arcs.length > 1) {
      result += "(" + arcs[0].pe.toString() + " " + arcs[0].target.id.value;
      if (!arcs[0].target.visited()) {
        arcs[0].target.visit();
        result += " " + arcs[0].target.toString();
      }
      for (int i = 1; i < arcs.length; i++) {
        result += " | " + arcs[i].pe.toString() + " " + arcs[i].target.id.value;
        if (!arcs[i].target.visited()) {
          arcs[i].target.visit();
          result += " " + arcs[i].target.toString();
        }
      }
      result += ")";
    }
    return result;
  }

  public void toTree(DefaultMutableTreeNode parent) {
    DefaultMutableTreeNode child;
    for (int i = 0; i < arcs.length; i++) {
      child = new DefaultMutableTreeNode(arcs[i].pe.toString() + " NODE_" + arcs[i].target.id.value);
      parent.add(child);
      if (!arcs[i].target.visited()) {
        arcs[i].target.visit();
        arcs[i].target.toTree(child);
      }
    }
  }

}
