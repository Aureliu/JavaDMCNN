// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.tree.*;

/**
 *  A view of the pattern as a tree.
 */

public class PatternView extends JFrame implements ActionListener {

  PatternCollection pc;
  DefaultMutableTreeNode patternsNode;
  DefaultMutableTreeNode patternSetsNode;
  JTree patternTree;
  JScrollPane jScrollPane = new JScrollPane();
  JFileChooser fc = new JFileChooser(".");
  File currentFile;
  Vector matchedPatterns = new Vector(); // Vector of String
  Vector appliedPatterns = new Vector(); // Vector of String

  public PatternView(File file) {
    currentFile = file;
    readPatterns();
    buildTree();
    jbInit();

    //Center the window
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension frameSize = this.getSize();
    if (frameSize.height > screenSize.height) {
      frameSize.height = screenSize.height;
    }
    if (frameSize.width > screenSize.width) {
      frameSize.width = screenSize.width;
    }
    this.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);

    setVisible(true);
  }

  public void actionPerformed(ActionEvent e) {
    if (e.getActionCommand().equals("Open ...")) {
      int returnVal = fc.showOpenDialog(this);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
        currentFile = fc.getSelectedFile();
        readPatterns();
        buildTree();
        patternTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        jScrollPane.getViewport().add(patternTree, null);
        this.setTitle("Pattern View - " + currentFile.getPath());
      }
    }
    else if (e.getActionCommand().equals("Expand")) {
      TreePath path = patternTree.getSelectionPath();
      if (path != null) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) path.getLastPathComponent();
        expand(root);
      }
    }
    else if (e.getActionCommand().equals("Collapse")) {
      TreePath path = patternTree.getSelectionPath();
      if (path != null) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) path.getLastPathComponent();
        collapse(root);
      }
    }
    else if (e.getActionCommand().equals("Expand All")) {
      expand(patternsNode);
      expand(patternSetsNode);
    }
    else if (e.getActionCommand().equals("Collapse All")) {
      collapse(patternsNode);
      collapse(patternSetsNode);
    }
    else if (e.getActionCommand().equals("Clear Marks")) {
      clearMatchedPatterns();
      clearAppliedPatterns();
      refresh();
    }
    else { // e.getActionCommand().equals("Exit")
      this.dispose();
    }
  }

  public void addMatchedPattern(String pattern) {
    matchedPatterns.add(pattern);
  }

  public void clearMatchedPatterns() {
    matchedPatterns = new Vector();
  }

  public void addAppliedPattern(String pattern) {
    appliedPatterns.add(pattern);
  }

  public void clearAppliedPatterns() {
    appliedPatterns = new Vector();
  }

  public void refresh() {
    /*System.out.println("matchedPatterns:");
    for (int i = 0; i < matchedPatterns.size(); i++) {
      System.out.println(matchedPatterns.get(i));
    }*/
    patternTree.updateUI();
  }

  private void buildTree() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    patternTree = new JTree(root);
    patternTree.setRootVisible(false);
    patternTree.setShowsRootHandles(true);
    patternTree.setEditable(false);
    patternTree.putClientProperty("JTree.lineStyle", "Angled");
    patternTree.setCellRenderer(new PatternRenderer());

    patternsNode = new DefaultMutableTreeNode("Patterns");
    patternSetsNode = new DefaultMutableTreeNode("Pattern Sets");
    root.add(patternsNode);
    root.add(patternSetsNode);

    DefaultMutableTreeNode parent;
    DefaultMutableTreeNode child;
    String name;
    PatternElement pe;
    PatternAlternation pa;
    PatternSet ps;
    PatternRule pr;
    Action action;

    for (int i = 0; i < pc.patternNames.size(); i++) {
      name = (String) pc.patternNames.get(i);
      child = new DefaultMutableTreeNode(name);
      patternsNode.add(child);
      parent = child;

      pe = (PatternElement) pc.patterns.get(name);
      if (pe instanceof PatternAlternation) {
        pa = (PatternAlternation) pe;
        for (int j = 0; j < pa.options.length; j++) {
          child = new DefaultMutableTreeNode(pa.options[j].toString());
          parent.add(child);
        }
      }
      else {
        child = new DefaultMutableTreeNode(pe.toString());
        parent.add(child);
      }
    }

    for (int i = 0; i < pc.patternSetNames.size(); i++) {
      name = (String) pc.patternSetNames.get(i);
      child = new DefaultMutableTreeNode("Pattern Set: " + name);
      patternSetsNode.add(child);
      parent = child;

      ps = (PatternSet) pc.patternSets.get(name);
      for (int j = 0; j < ps.rules.size(); j++) {
        pr = (PatternRule) ps.rules.get(j);
        child = new DefaultMutableTreeNode(pr.patternName);
        parent.add(child);

        for (int k = 0; k < pr.actions().size(); k++) {
          action = (Action) pr.actions().get(k);
          child.add(new DefaultMutableTreeNode(action));
        }
      }
    }
    patternTree.updateUI();
  }

  private void readPatterns() {
    pc = new PatternCollection();
    try {
      pc.readPatternCollection(new BufferedReader(new FileReader(currentFile)));
    }
    catch (IOException e) {
      System.err.println("Error: reading pattern file " +
        currentFile.getName() + ", " + e.getMessage());
    }
    pc.makePatternGraph();
  }

  private void expand(DefaultMutableTreeNode root) {
    patternTree.expandPath(new TreePath(root.getPath()));
    for (int i = 0; i < root.getChildCount(); i++) {
      expand((DefaultMutableTreeNode) root.getChildAt(i));
    }
  }

  private void collapse(DefaultMutableTreeNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      collapse((DefaultMutableTreeNode) root.getChildAt(i));
    }
    patternTree.collapsePath(new TreePath(root.getPath()));
  }

  private void jbInit() {
    // Menu Bar
    JMenuBar jmb = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic(KeyEvent.VK_F);
    JMenuItem item;

    item = new JMenuItem("Open ...");
    item.setMnemonic(KeyEvent.VK_O);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
    fileMenu.add(item);
    item.addActionListener(this);

    fileMenu.addSeparator();

    item = new JMenuItem("Exit");
    item.setMnemonic(KeyEvent.VK_X);
    fileMenu.add(item);
    item.addActionListener(this);

    JMenu viewMenu = new JMenu("View");
    viewMenu.setMnemonic(KeyEvent.VK_V);

    item = new JMenuItem("Expand");
    item.setMnemonic(KeyEvent.VK_E);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, ActionEvent.CTRL_MASK));
    viewMenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Collapse");
    item.setMnemonic(KeyEvent.VK_C);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
    viewMenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Expand All");
    item.setMnemonic(KeyEvent.VK_E);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, ActionEvent.CTRL_MASK + ActionEvent.ALT_MASK));
    viewMenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Collapse All");
    item.setMnemonic(KeyEvent.VK_C);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK + ActionEvent.ALT_MASK));
    viewMenu.add(item);
    item.addActionListener(this);

    viewMenu.addSeparator();

    item = new JMenuItem("Clear Marks");
    item.setMnemonic(KeyEvent.VK_M);
    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, ActionEvent.CTRL_MASK));
    viewMenu.add(item);
    item.addActionListener(this);

    jmb.add(fileMenu);
    jmb.add(viewMenu);
    setJMenuBar(jmb);

    // file chooser
    Jet.Concepts.ConcreteFileFilter filter =
      new Jet.Concepts.ConcreteFileFilter("pat", "Pattern Files (*.pat)");
    fc.setFileFilter(filter);
    fc.setDialogTitle("Open Pattern File");
    fc.setApproveButtonText("Open");
    fc.setApproveButtonMnemonic(KeyEvent.VK_O);
    fc.setApproveButtonToolTipText("Open Pattern File");

    // content pane
    patternTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    jScrollPane.getViewport().add(patternTree, null);
    this.getContentPane().setLayout(new BorderLayout());
    this.getContentPane().add(jScrollPane, BorderLayout.CENTER);
    this.setSize(600, 600);
    this.setTitle("Pattern View - " + currentFile.getPath());
    this.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        PatternView.this.dispose();
      }
    });
  }

  class PatternRenderer extends DefaultTreeCellRenderer {
    ImageIcon actionIcon;
    ImageIcon matchIcon;
    ImageIcon applyIcon;

    PatternRenderer() {
      actionIcon = new ImageIcon("images/fish.gif");
      matchIcon = new ImageIcon("images/octopus.gif");
      applyIcon = new ImageIcon("images/crab.gif");
    }

    public Component getTreeCellRendererComponent(JTree tree, Object value,
      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      if (leaf && isAction(value))
        setIcon(actionIcon);
      else if (!leaf && isMatched(value))
        setIcon(matchIcon);
      else if (!leaf && isApplied(value))
        setIcon(applyIcon);
      return this;
    }

    boolean isAction(Object value) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      TreeNode[] path = node.getPath();
      for (int i = 0; i < path.length; i++) {
        if (((DefaultMutableTreeNode) path[i]).equals(patternSetsNode))
          return true;
      }
      return false;
    }

    boolean isMatched(Object value) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      String info = node.getUserObject().toString();
      if (!matchedPatterns.contains(info))
        return false;
      TreeNode[] path = node.getPath();
      for (int i = 0; i < path.length; i++) {
        if (((DefaultMutableTreeNode) path[i]).equals(patternsNode))
          return true;
      }
      return false;
    }

    boolean isApplied(Object value) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      String info = node.getUserObject().toString();
      if (!appliedPatterns.contains(info))
        return false;
      TreeNode[] path = node.getPath();
      for (int i = 0; i < path.length; i++) {
        if (((DefaultMutableTreeNode) path[i]).equals(patternSetsNode))
          return true;
      }
      return false;
    }
  }

}
