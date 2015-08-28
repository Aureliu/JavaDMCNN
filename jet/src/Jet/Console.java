// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import Jet.Tipster.*;
import Jet.Lex.Tokenizer;
import Jet.Parser.Parsers;
import Jet.Pat.PatternApplication;
import Jet.Pat.PatternView;
import Jet.Pat.PatternGraphView;
import Jet.Scorer.SGMLScorerWindow;
import Jet.Concepts.ConceptHierarchyWindow;
import Jet.HMM.HMMTagger;

/**
 *  console (main window) for interactive use of JET.
 */

public class Console extends JFrame implements ActionListener {
  static JMenuBar menuBar;
  static JPanel controlPanel;
  static JLabel label;
  static JTextField sentence;
  static JTextArea area = null;
  static JScrollPane sp;
  public static PatternView pv = null;
  public static PatternGraphView pgv = null;

  public Console() {
    super ("Jet Console");

    try {
      jbInit();
    }
    catch(Exception e) {
      e.printStackTrace();
    }

    setSize (640,480);

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

    setVisible (true);
  }

  private static JMenu buildFileMenu () {
    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic(KeyEvent.VK_F);

    JMenuItem loadGrammar = new JMenuItem ("Reload Grammar");
    loadGrammar.setMnemonic(KeyEvent.VK_G);
    loadGrammar.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          JetTest.readGrammar();
        }
      }
    );
    fileMenu.add (loadGrammar);

    JMenuItem loadLexicons = new JMenuItem ("Reload Lexicon");
    loadLexicons.setMnemonic(KeyEvent.VK_L);
    loadLexicons.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          JetTest.readLexicons();
        }
      }
    );
    fileMenu.add (loadLexicons);

    JMenuItem loadPatterns = new JMenuItem ("Reload Patterns");
    loadPatterns.setMnemonic(KeyEvent.VK_P);
    loadPatterns.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          JetTest.readPatterns();
          JMenu patternsMenu = menuBar.getMenu(3);
          patternsMenu.remove(2); // remove old "Pattern Match Trace" submenu
          patternsMenu.remove(2); // remove old "Pattern Apply Trace" submenu
          patternsMenu.add(PatternApplication.matchSubmenu());
          patternsMenu.add(PatternApplication.applySubmenu());
        }
      }
    );
    fileMenu.add (loadPatterns);

	fileMenu.addSeparator();

    JMenuItem clearConsole = new JMenuItem ("Clear console");
    clearConsole.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          area.setText("");
        }
      }
    );

    fileMenu.add (clearConsole);

    fileMenu.addSeparator();

    JMenuItem exit = new JMenuItem ("Exit");
    exit.setMnemonic(KeyEvent.VK_X);
    exit.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          System.exit (0);
        }
      }
    );
    fileMenu.add (exit);

    return fileMenu;
  }

  private static JMenu buildTaggerMenu () {
  	JMenu taggerMenu = new JMenu("Tagger");
  	taggerMenu.setMnemonic(KeyEvent.VK_G);

  	final JCheckBoxMenuItem traceItem
      = new JCheckBoxMenuItem ("POS Tagger Trace", HMMTagger.trace);
    traceItem.setMnemonic(KeyEvent.VK_P);
    traceItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          HMMTagger.trace = traceItem.getState();
        }
      }
    );
    taggerMenu.add (traceItem);
    return taggerMenu;
  }

  private static JMenu buildPatternsMenu () {
    JMenu patternsMenu = new JMenu("Patterns");
    patternsMenu.setMnemonic(KeyEvent.VK_P);

    JMenuItem viewPatterns = new JMenuItem ("View Patterns");
    viewPatterns.setMnemonic(KeyEvent.VK_V);
    viewPatterns.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          pv = new PatternView(JetTest.patternFile);
        }
      }
    );
    patternsMenu.add(viewPatterns);

    JMenuItem viewPatternGraphs = new JMenuItem ("View Pattern Graphs");
    viewPatternGraphs.setMnemonic(KeyEvent.VK_G);
    viewPatternGraphs.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          pgv = new PatternGraphView(JetTest.patternFile);
        }
      }
    );
    patternsMenu.add(viewPatternGraphs);

    patternsMenu.add(PatternApplication.matchSubmenu());
    patternsMenu.add(PatternApplication.applySubmenu());

    return patternsMenu;
  }

  private static JMenu buildToolsMenu () {
    JMenu toolsMenu = new JMenu("Tools");
    toolsMenu.setMnemonic(KeyEvent.VK_T);

    JMenuItem sgmlScorer = new JMenuItem ("SGML Scorer ...");
    sgmlScorer.setMnemonic(KeyEvent.VK_S);
    sgmlScorer.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          SGMLScorerWindow dsw = new SGMLScorerWindow();
        }
      }
    );
    toolsMenu.add (sgmlScorer);

    toolsMenu.addSeparator();

    JMenuItem conceptEditor = new JMenuItem ("Concept Editor ...");
    conceptEditor.setMnemonic(KeyEvent.VK_C);
    conceptEditor.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          ConceptHierarchyWindow chw = new ConceptHierarchyWindow
            (JetTest.conceptHierarchy, JetTest.conceptHierarchyFile);
        }
      }
    );
    toolsMenu.add (conceptEditor);

    toolsMenu.addSeparator();

    JMenu processDocsSubMenu = new JMenu ("Process Documents");
    processDocsSubMenu.setMnemonic(KeyEvent.VK_P);
    toolsMenu.add (processDocsSubMenu);

    JMenuItem view = new JMenuItem ("and View Annotated Documents ...");
    view.setMnemonic(KeyEvent.VK_V);
    view.addActionListener (
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          if (pv != null) {
            pv.clearMatchedPatterns();
            pv.clearAppliedPatterns();
            pv.refresh();
          }
          JetTest.processFiles (true);
        }
      }
    );
    processDocsSubMenu.add (view);

    JMenuItem noview = new JMenuItem ("but Don't View");
    noview.setMnemonic(KeyEvent.VK_D);
    noview.addActionListener (
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          if (pv != null) {
            pv.clearMatchedPatterns();
            pv.clearAppliedPatterns();
            pv.refresh();
          }
          JetTest.processFiles (false);
        }
      }
    );
    processDocsSubMenu.add (noview);

    JMenuItem closeAllViews = new JMenuItem ("Close All Jet Documents");
    closeAllViews.setMnemonic(KeyEvent.VK_A);
    closeAllViews.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, ActionEvent.CTRL_MASK));
    closeAllViews.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          JetTest.closeAllViews();
        }
      }
    );
    toolsMenu.add(closeAllViews);

    JMenuItem setColors = new JMenuItem ("Costomize Annotation Colors ...");
    setColors.setMnemonic(KeyEvent.VK_Z);
    setColors.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          AnnotationColor.showColors();
        }
      }
    );
    toolsMenu.add(setColors);

    return toolsMenu;
  }

/**
 *  if a Console has been created (through the Console constructor), write
 *  <I>stg</I> to the console, else write it to System.err.
 */

  public static void print (String stg) {
    if (area == null) {
      System.err.print (stg);
    } else {
      area.append (stg);
      sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum());
    }
  }

/**
 *  if a Console has been created (through the Console constructor), write
 *  <I>stg</I> + newline to the console, else write it to System.err.
 */

  public static void println (String stg) {
    if (area == null) {
      System.err.println (stg);
    } else {
      area.append (stg);
      area.append ("\n");
      sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum());
    }
  }

  public void actionPerformed (ActionEvent e) {
    String stg = sentence.getText();
    println ("---------");
    println ("Sentence:                               " + stg );
    sentence.selectAll();
    if (pv != null) {
      pv.clearMatchedPatterns();
      pv.clearAppliedPatterns();
      pv.refresh();
    }
    if (stg == null) return;
    Document doc = new Document(stg);
    Span sentenceSpan = new Span (0, doc.length());
    Tokenizer.tokenize (doc, sentenceSpan);
    Control.processSentence (doc, sentenceSpan);
  }

  private void jbInit() throws Exception {
    this.setDefaultCloseOperation(3);

    menuBar = new JMenuBar();
    menuBar.add(buildFileMenu());
    menuBar.add(Parsers.parserMenu());
    menuBar.add(buildTaggerMenu());
    menuBar.add(buildPatternsMenu());
    menuBar.add(buildToolsMenu());
    setJMenuBar(menuBar);

    controlPanel = new JPanel();
    label = new JLabel();
    sentence = new JTextField();
    area = new JTextArea();
    area.setEditable(false);
    area.setFont(new Font("Monospaced",Font.PLAIN,12));
    sp = new JScrollPane(area);

    Container c = this.getContentPane();

    c.setLayout(new BorderLayout());

    label.setText("Enter sentence:");
    sentence.addActionListener (this);
    controlPanel.setLayout(new GridBagLayout());
    area.setMargin(new Insets(3, 3, 3, 3));
    c.add(controlPanel, BorderLayout.SOUTH);
    controlPanel.add(label, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0,
            GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 15, 3, 0), 0, 0));
    controlPanel.add(sentence, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0,
            GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(3, 6, 3, 16), 0, 0));
    // this.getContentPane().add(area, BorderLayout.CENTER);
    c.add(sp, BorderLayout.CENTER);
  }

}
