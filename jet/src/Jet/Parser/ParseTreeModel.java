// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

import javax.swing.tree.*;
import javax.swing.event.*;
import Jet.Parser.ParseTreeNode;

/**
 *  provides an implementation of the Java TreeModel for parse trees.
 *  This enables the display of parse trees by class ParseTreeView using
 *  the Java tree drawing facility.
 */

public class ParseTreeModel implements TreeModel {

  ParseTreeNode root;

  public ParseTreeModel(ParseTreeNode root) {
    this.root = root;
  }

  public Object getChild (Object parent, int index) {
    return ((ParseTreeNode)parent).children[index];
  }

  public int getChildCount (Object parent) {
    return ((ParseTreeNode)parent).children.length;
  }

  public int getIndexOfChild (Object parent, Object child) {
    Object[] children = ((ParseTreeNode)parent).children;
    for (int i=0; i<children.length; i++) {
      if (child.equals(children[i])) return i;
    }
    return -1;
  }

  public Object getRoot() {
    return root;
  }

  public boolean isLeaf (Object node){
    return (((ParseTreeNode)node).children == null);
  }

  public void valueForPathChanged (TreePath path, Object newValue) {
  }

  public void addTreeModelListener (TreeModelListener l) {
  }

  public void removeTreeModelListener (TreeModelListener l) {
  }
}
