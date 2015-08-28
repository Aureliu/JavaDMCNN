// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

import javax.swing.tree.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 *  a display of a parse tree using the Java tree drawing facility.
 */

public class ParseTreeView extends JFrame {

  JTree tree;
  ParseTreeModel treeModel;

  /**
   *  create (and make visible) a JFrame containing a display of the
   *  parse tree with root <I>root</I>.
   */

  public ParseTreeView(String title, ParseTreeNode root) {
    super (title);
    setSize (400, 300);
    // addWindowListener (new BasicWindowMonitor());
    treeModel = new ParseTreeModel(root);
    tree = new JTree(treeModel);
    getContentPane().add(tree, BorderLayout.CENTER);
    this.setVisible(true);
  }
}
