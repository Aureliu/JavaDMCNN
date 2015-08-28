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

public class PatternGraphView extends JFrame implements ActionListener {

  PatternCollection pc;
  JTree patternGraphTree;
  JScrollPane jScrollPane = new JScrollPane();
  JFileChooser fc = new JFileChooser(".");
  File currentFile;

  public PatternGraphView(File file) {
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
        patternGraphTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        jScrollPane.getViewport().add(patternGraphTree, null);
        this.setTitle("Pattern Graph View - " + currentFile.getPath());
      }
    }
    else if (e.getActionCommand().equals("Expand")) {
      TreePath path = patternGraphTree.getSelectionPath();
      if (path != null) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) path.getLastPathComponent();
        expand(root);
      }
    }
    else if (e.getActionCommand().equals("Collapse")) {
      TreePath path = patternGraphTree.getSelectionPath();
      if (path != null) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) path.getLastPathComponent();
        collapse(root);
      }
    }
    else if (e.getActionCommand().equals("Expand All")) {
      DefaultMutableTreeNode root =
        (DefaultMutableTreeNode) patternGraphTree.getModel().getRoot();
      for (int i = 0; i < root.getChildCount(); i++) {
        expand((DefaultMutableTreeNode) root.getChildAt(i));
      }
    }
    else if (e.getActionCommand().equals("Collapse All")) {
      DefaultMutableTreeNode root =
        (DefaultMutableTreeNode) patternGraphTree.getModel().getRoot();
      for (int i = 0; i < root.getChildCount(); i++) {
        collapse((DefaultMutableTreeNode) root.getChildAt(i));
      }
    }
    else { // e.getActionCommand().equals("Exit")
      this.dispose();
    }
  }

  private void buildTree() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("Pattern Sets");
    patternGraphTree = new JTree(root);
    patternGraphTree.setRootVisible(true);
    patternGraphTree.setShowsRootHandles(true);
    patternGraphTree.setEditable(false);
    patternGraphTree.putClientProperty("JTree.lineStyle", "Angled");
    patternGraphTree.setCellRenderer(new PatternGraphRenderer());

    DefaultMutableTreeNode parent;
    DefaultMutableTreeNode child;
    String name;
    PatternSet ps;

    for (int i = 0; i < pc.patternSetNames.size(); i++) {
      name = (String) pc.patternSetNames.get(i);
      child = new DefaultMutableTreeNode("Pattern Set: " + name);
      root.add(child);
      parent = child;

      child = new DefaultMutableTreeNode("NODE_0");
      parent.add(child);
      parent = child;

      ps = (PatternSet) pc.patternSets.get(name);
      ps.rootNode.toTree(parent);
    }
    patternGraphTree.updateUI();

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
    patternGraphTree.expandPath(new TreePath(root.getPath()));
    for (int i = 0; i < root.getChildCount(); i++) {
      expand((DefaultMutableTreeNode) root.getChildAt(i));
    }
  }

  private void collapse(DefaultMutableTreeNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      collapse((DefaultMutableTreeNode) root.getChildAt(i));
    }
    patternGraphTree.collapsePath(new TreePath(root.getPath()));
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
    patternGraphTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    jScrollPane.getViewport().add(patternGraphTree, null);
    this.getContentPane().setLayout(new BorderLayout());
    this.getContentPane().add(jScrollPane, BorderLayout.CENTER);
    this.setSize(600, 600);
    this.setTitle("Pattern Graph View - " + currentFile.getPath());
    this.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        PatternGraphView.this.dispose();
      }
    });
  }

  class PatternGraphRenderer extends DefaultTreeCellRenderer {
    ImageIcon actionIcon;

    PatternGraphRenderer() {
      actionIcon = new ImageIcon("images/fish.gif");
    }

    public Component getTreeCellRendererComponent(JTree tree, Object value,
      boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      if (leaf && isAction(value))
        setIcon(actionIcon);
      return this;
    }

    boolean isAction(Object value) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      String info = (String) node.getUserObject();
      if (info.indexOf("-->") >= 0) {
        return true;
      }
      return false;
    }
  }

}

