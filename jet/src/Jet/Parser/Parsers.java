// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2001
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import Jet.Console;
import Jet.Lisp.*;
import Jet.Tipster.*;

/**
 *  contains static methods for various types of recognizers
 *  and parsers.
 */

public class Parsers {

  static final String NULL = "null";
  static final String SENTENCE = "sentence";

  /**
   *  a constant indicating that the top-down recognizer, <CODE>recognize</CODE>,
   *  should be used to analyze the text
   */

  public static final int RECOGNIZE = 1;

  /**
   *  a constant indicating that the top-down parser, <CODE>TDParse</CODE>,
   *  should be used to analyze the text
   */

  public static final int TDPARSE = 2;

  /**
   *  a constant indicating that the bottom-up parser, <CODE>BUParse</CODE>,
   *  should be used to analyze the text
   */

  public static final int BUPARSE = 3;


  /**
   *  a constant indicating that the top-down active chart parser,
   *  <CODE>chartParse</CODE>, should be used to analyze the text
   */

  public static final int CHARTPARSE = 4;

  /**
   *  determines which analyzer will be applied by method <CODE>parse</CODE>
   */

  public static int parserType = RECOGNIZE;

  static Vector parses = new Vector();

  /**
   *  if <B>true</B>, causes each parser to write trace messages to the
   *  Console.
   */

  public static boolean parserTrace = true;

  /**
   *  parse characters <I>posn</I> to <I>end</I> of <I>Document</I> using
   *  grammar <I>gram</I>.  The type of recognizer / parser used is determined
   *  by the value of <I>parserType</I>.
   *  @return a Vector of parses, where each parse is a ParseTreeNode (the
   *           root of the parse tree.  If no parses are found, an empty
   *           Vector is returned.
   */

  public static Vector parse (Document doc, int posn, int end, Grammar gram) {
    switch (parserType) {
      case RECOGNIZE: parses = new Vector();
                      if (recognize (doc, posn, end, gram)) parses.addElement(null);
                      return parses;
      case TDPARSE:   return TDParse   (doc, posn, end, gram);
      case BUPARSE:   return BUParse   (doc, posn, end, gram);
      case CHARTPARSE: return chartParse (doc, posn, end, gram);
      default:        return new Vector();
    }
  }

//----- t o p - d o w n   r e c o g n i z e r

  /**
   *  apply a top-down recognizer to characters <I>posn</I> to <I>end</I>
   *  of <I>Document</I> using grammar <I>gram</I>.
   *  @return true if these characters can be recognized as a SENTENCE.
   */

  public static boolean recognize (Document doc, int posn, int end,
                                   Grammar gram) {
    Stack goals = new Stack();
    goals.push(SENTENCE);
    return recognize (doc, posn, end, gram, goals);
  }

  private static boolean recognize (Document doc, int posn, int end,
                                   Grammar gram, Stack goals) {
    if (goals.empty()) {
      if (posn == end) {
        Console.println ("Sentence recognized.");
        return true;
      } else {
        return false;
      }
    }

    Object goal = goals.pop();
    printParseTrace ("Seeking " + goal, posn, 0);
    if (goal == NULL) {
      return recognize (doc, posn, end, gram, goals);
    }

    // if the goal is a Literal, look if there is a token starting at the
    // current position whose text matches this string.
    if (goal instanceof Literal) {
      Annotation token = doc.tokenAt (posn);
      if (token != null && ((Literal)goal).getString().equals(doc.text(token).trim())) {
        posn = token.span().end();
        return recognize (doc, posn, end, gram, goals);
      } else {
        return false;
      }
    }

    // otherwise goal should be String:  a grammar category
    // check first whether there is a constituent annotation starting at the
    // current position with this grammar category (this annotation may have
    // been created either by lexicon lookup or pattern matching)
    Vector constits = doc.annotationsAt(posn, "constit");
    if (constits != null) {
      for (int i = 0; i < constits.size(); i++) {
        Annotation constit = (Annotation) constits.elementAt(i);
        if (constit.get("cat") == goal) {
          posn = constit.span().end();
          return recognize (doc, posn, end, gram, goals);
        }
      }
    }

    // if not, look up the definition of this symbol in the grammar
    Vector productions = gram.getProductions((String) goal);
    if (productions != null) {
    // if it is defined (a non-terminal symbol), take each production
    // in turn, treat the right-hand side of the production as a set of
    // goals to be satisfied, and add these goals to the goal stack
      for (int i = 0; i < productions.size(); i++) {
        Production production = (Production) productions.elementAt(i);
        Vector rhs = production.rhs();
        Stack newgoals = (Stack) goals.clone();
        for (int j = (rhs.size()-1); j >= 0 ; j--) {
          newgoals.push(rhs.elementAt(j));
        }
        if (recognize (doc, posn, end, gram, newgoals)) {
          return true;
        }
      }
    }

    return false;
  }

  //----  t o p - d o w n   p a r s e r

  /**
   *  apply a top-down backtracking parser to characters <I>posn</I> to
   *  <I>end</I> of <I>Document</I> using grammar <I>gram</I>.
   *  @return a Vector of the parses for these characters
   */

  public static Vector TDParse (Document doc, int posn, int end,
                                   Grammar gram) {
    Stack goals = new Stack();
    Stack trees = new Stack();
    parses = new Stack();
    goals.push(SENTENCE);
    TDParse (doc, posn, end, gram, goals, trees);
    Console.println (parses.size() + " parse(s) obtained");
    return parses;
  }

  private static void TDParse (Document doc, int posn, int end,
                Grammar gram, Stack goals, Stack trees) {
    if (goals.empty()) {
      if (posn == end) {
        if (parserTrace) Console.println ("Sentence parsed.");
        parses.addElement(trees.pop());
      }
      return;
    }

    Object goal = goals.pop();

    if (goal instanceof Reduce) {
      String category = ((Reduce)goal).category;
      int nChildren = ((Reduce)goal).numberOfChildren;
      ParseTreeNode [] children = new ParseTreeNode[nChildren];
      for (int i = nChildren-1; i >=0; i--) {
        children[i] = (ParseTreeNode)(trees.pop());
      }
      int start = children[0].start;
      trees.push (new ParseTreeNode(category, children, start, posn, null, null));
      printParseTrace ("Found   " + category + " = "
                       + doc.text(new Span(start, posn)), start, posn);
      TDParse (doc, posn, end, gram, goals, trees);
      return;
    }

    printParseTrace ("Seeking " + goal, posn, 0);

    if (goal == NULL) {
      trees.push(new ParseTreeNode(NULL, null, posn, posn, null, null));
      TDParse (doc, posn, end, gram, goals, trees);
      return;
    }

    if (goal instanceof Literal) {
      Annotation token = doc.tokenAt (posn);
      if (token != null && ((Literal)goal).getString().equals(doc.text(token).trim())) {
        int newposn = token.span().end();
        trees.push (new ParseTreeNode(goal, null, posn, newposn, token, (String)goal));
        printParseTrace ("Found   " + goal, posn, newposn);
        TDParse (doc, newposn, end, gram, goals, trees);
      }
      return;
    }

    // otherwise goal should be String (a grammar category)
    String cat = (String) goal;
    Vector constits = doc.annotationsAt(posn, "constit");
    if (constits != null) {
      for (int i = 0; i < constits.size(); i++) {
        Annotation constit = (Annotation) constits.elementAt(i);
        if (constit.get("cat") ==  cat) {
          int newposn = constit.span().end();
          trees.push (new ParseTreeNode(cat, null, posn, newposn, constit, doc.text(constit)));
          printParseTrace ("Found   " + goal + " = "
                           + doc.text(new Span(posn, newposn)), posn, newposn);
          TDParse (doc, newposn, end, gram, goals, trees);
          return;
        }
      }
    }

    //  expand symbol using productions of grammar
    Vector productions = gram.getProductions(cat);
    if (productions != null) {
      for (int i = 0; i < productions.size(); i++) {
        Production production = (Production) productions.elementAt(i);
        Vector rhs = production.rhs();
        Stack newgoals = (Stack) goals.clone();
        Stack newtrees = (Stack) trees.clone();
        newgoals.push(new Reduce(cat, rhs.size()));
        for (int j = (rhs.size()-1); j >= 0 ; j--) {
          newgoals.push(rhs.elementAt(j));
        }
        TDParse (doc, posn, end, gram, newgoals, newtrees);
      }
    }
    return;
  }

  //-----  b o t t o m - u p   p a r s e r ------------------

  static Stack agenda;
  static Vector chart;

  /**
   *  apply a bottom-up ('immediate constituent') parser to characters
   *  <I>posn</I> to <I>end</I> of <I>Document</I> using grammar <I>gram</I>.
   *  @return  a Vector of the parses for these characters
   */

  public static Vector BUParse (Document doc, int posn, int end, Grammar gram) {
    int start = posn;
    agenda = new Stack();
    chart = new Vector();
    parses = new Vector();
    while (posn >= 0 && posn < end) {
      posn = addLexicalNodes (doc, posn);
      while (! (agenda.empty()) ) {
        ParseTreeNode node = (ParseTreeNode) (agenda.pop());
        Vector prods = gram.getProductionsEndingIn(node.category);
        if (prods != null) {
          for (int i=0; i<prods.size(); i++) {
            Production prod = (Production) prods.elementAt(i);
            int prodLength = prod.rhs.size();
            ParseTreeNode [] constituents = new ParseTreeNode[prodLength];
            constituents[prodLength-1] = node;
            extend (doc, prod, node.start, node.end, constituents, prodLength-2);
          }
        }
        if (node.category == SENTENCE && node.start == start && node.end == end)
          parses.addElement(node);
      }
    }
    Console.println (parses.size() + " parse(s) obtained");
    return parses;
  }

  /**
   *  adds to the chart ParseTreeNodes corresponding to the token starting
   *  at position <I>posn</I>.  One node is added for the string, and
   *  one node is added for each <B>constit</B> annotation (for each
   *  lexical entry or pattern matching this word).
   */

  private static int addLexicalNodes (Document doc, int posn) {
    Annotation token = doc.tokenAt(posn);
    if (token == null) return -1;
    addNode (doc, doc.text(token).trim(), null, posn, token.span().end(),
             token, doc.text(token));
    Vector constits = doc.annotationsAt(posn, "constit");
    if (constits != null) {
      for (int i=0; i<constits.size(); i++) {
        Annotation constit = (Annotation) constits.elementAt(i);
        addNode (doc, constit.get("cat"), null, posn, constit.span().end(),
                constit, doc.text(constit));
      }
    }
    return token.span().end();
  }

  private static void addNode (Document doc, Object category, ParseTreeNode[] children,
                               int start, int end, Annotation ann, String word) {
    if (category instanceof String) {
      printParseTrace ("Adding " + category, start, end);
    } else {
      printParseTrace ("Adding " + category + " = " + doc.text(new Span(start, end)), start, end);
    }
    ParseTreeNode node = new ParseTreeNode (category, children, start, end, ann, word);
    chart.addElement(node);
    if (parserType == BUPARSE) agenda.push(node);
  }

  private static void extend (Document doc, Production prod, int start, int end,
                              ParseTreeNode[] constituents, int n) {
    if (n >= 0) {
      Object element = prod.rhs.elementAt(n);
      for (int i=0; i<chart.size(); i++) {
        ParseTreeNode node = (ParseTreeNode) chart.elementAt(i);
        if (node.category == element && node.end == start) {
          constituents[n] = node;
          extend (doc, prod, node.start, end, constituents, n-1);
        }
      }
    } else {
      ParseTreeNode[] c = (ParseTreeNode[])constituents.clone();
      addNode (doc, prod.lhs, c, start, end, null, null);
    }
  }

  // ----------  t o p - d o w n   a c t i v e   c h a r t   p a r s e r -------

  static Hashtable sought;

  /**
   *  apply a top-down active chart parser to characters
   *  <I>posn</I> to <I>end</I> of Document <I>doc</I> using grammar <I>gram</I>.
   *  @return  a Vector of the parses for these characters
   */

  public static Vector chartParse (Document doc, int posn, int end, Grammar gram) {
    int start = posn;
    agenda = new Stack();
    chart = new Vector();
    parses = new Vector();
    sought = new Hashtable();
    while (posn >= 0 && posn < end) {
      posn = addLexicalNodes (doc, posn);
      }
    seek (SENTENCE, start, gram);
    while (! (agenda.empty()) ) {
      Edge edge = (Edge) agenda.pop();
      chart.addElement(edge);
      if (edge.end > edge.start) {
        printParseTrace ("Adding " + edge + " = " + doc.text(new Span(edge.start, edge.end)),
                         edge.start, edge.end);
      } else {
        printParseTrace ("Adding " + edge, edge.start, edge.end);
      }
      if (edge instanceof ParseTreeNode) {
        ParseTreeNode node = (ParseTreeNode) edge;
        if (node.category == SENTENCE && node.start == start && node.end == end)
            parses.addElement(node);
      }
      if (edge instanceof ActiveEdge) {
        extendActiveEdge ((ActiveEdge) edge, gram);
      } else {
        extendInactiveEdge ((ParseTreeNode) edge, gram);
      }
    }
    Console.println (parses.size() + " parse(s) obtained");
    return parses;
  }

  private static void seek (String cat, int posn, Grammar gram) {
    printParseTrace ("Seeking " + cat, posn, 0);
    if (!(sought.containsKey(cat))) sought.put(cat, new Hashtable());
    Hashtable soughtCat = (Hashtable) sought.get(cat);
    soughtCat.put(new Integer(posn),Boolean.TRUE);
    Vector productions = gram.getProductions(cat);
    if (productions != null) {
      for (int i = 0; i < productions.size(); i++) {
        Production production = (Production) productions.elementAt(i);
        Vector rhs = production.rhs();
        ActiveEdge e = new ActiveEdge (cat, rhs, new ParseTreeNode[0], posn, posn);
        agenda.push(e);
      }
    }
  }

  private static boolean getSought (String cat, int posn) {
    Hashtable soughtCat = (Hashtable) sought.get(cat);
    if (soughtCat == null) {
      return false;
    } else {
      return soughtCat.containsKey(new Integer (posn));
    }
  }

  /**
   *  invoked when we take an active edge <I>edge</I> off the agenda and
   *  try to extend it.  If the next element to be matched in the production
   *  is a non-terminal symbol, and we haven't done a seek on this symbol
   *  at this position yet, do so now.  Otherwise, look for inactive edges
   *  (ParseTreeNodes) in the chart which can be used to extend this edge.
   */

  private static void extendActiveEdge (ActiveEdge edge, Grammar gram) {
    Object needed = edge.needs();
    int posn = edge.end;
    if (needed instanceof String) {
      String cat = (String) needed;
      if (gram.defines(cat) && !getSought(cat, posn)) {
        seek (cat, posn, gram);
        return;
      }
    }
    for (int i=0; i<chart.size(); i++) {
      if (chart.elementAt(i) instanceof ParseTreeNode) {
        ParseTreeNode node = (ParseTreeNode) chart.elementAt(i);
        if (node.start == posn && node.category == needed) {
          extendEdge (edge, node, gram);
        }
      }
    }
  }

  /**
   *  invoked when we take an inactive edge (ParseTreeNode <I>node</I>) off
   *  the agenda and try to extend it.  Look for all active edges which
   *  end at the starting point of this node and which need an inactive
   *  edge of this category to be extended.
   */

  private static void extendInactiveEdge (ParseTreeNode node, Grammar gram) {
    int posn = node.start;
    for (int i=0; i<chart.size(); i++) {
      if (chart.elementAt(i) instanceof ActiveEdge) {
        ActiveEdge edge = (ActiveEdge) chart.elementAt(i);
        if (edge.end == posn && edge.needs() == node.category) {
          extendEdge (edge, node, gram);
        }
      }
    }
  }

  /**
   *  applies the 'fundamental rule' to combine an active edge and an inactive
   *  edge (where the end vertex of the active edge matches the start vertex
   *  of the inactive edge) to create a new edge;  the new edge is added to
   *  the agenda.
   */

  private static void extendEdge (ActiveEdge edge, ParseTreeNode node, Grammar gram) {
    if (parserTrace) Console.println ("Extending " + edge + " with " + node);
    ParseTreeNode[] children = edge.children;
    ParseTreeNode[] newChildren = new ParseTreeNode[children.length+1];
    for (int i=0; i<children.length; i++) newChildren[i] = children[i];
    newChildren[children.length] = node;
    if (newChildren.length == edge.rhs.size()) {
      ParseTreeNode newNode = new ParseTreeNode (edge.category, newChildren,
                                                 edge.start, node.end, null, null);
      agenda.push(newNode);
    } else {
      ActiveEdge newEdge = new ActiveEdge (edge.category, edge.rhs, newChildren,
                                           edge.start, node.end);
      agenda.push(newEdge);
    }
  }

  // ----------  p a r s e r   t r a c e  ----------

  private static void printParseTrace (String message, int start, int end) {
    StringBuffer sb;
    if (parserTrace) {
      if (message.length() < 40) {
        sb = new StringBuffer (message);
        for (int i=message.length(); i < 40; i++) sb.append(' ');
      } else {
        Console.println (message);
        sb = new StringBuffer ("                                        ");
      }
      for (int i=0; i<start; i++) sb.append(' ');
      if (start >= end) {
        sb.append ('+');
      } else {
        for (int i=start; i<end; i++) sb.append ('=');
      }
      Console.println (sb.toString());
    }
  }

  // ----------  p a r s e r   m e n u ----------

  /**
   *  returns a menu for controlling the parser (part of the Console menu
   *  bar).  Includes menu items for selecting the parserType, controlling
   *  the parserTrace, and drawing a parse tree.
   */

  public static JMenu parserMenu () {
    JMenu menu = new JMenu("Parser");
    menu.setMnemonic(KeyEvent.VK_A);
    JRadioButtonMenuItem recognizerItem = new JRadioButtonMenuItem ("Use Recognizer");
    recognizerItem.setMnemonic(KeyEvent.VK_R);
    recognizerItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          parserType = RECOGNIZE;
        }
      }
    );
    recognizerItem.setSelected (true);
    menu.add (recognizerItem);
    JRadioButtonMenuItem TDParserItem = new JRadioButtonMenuItem ("Use Top-Down Parser");
    TDParserItem.setMnemonic(KeyEvent.VK_T);
    TDParserItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          parserType = TDPARSE;
        }
      }
    );
    menu.add (TDParserItem);
    JRadioButtonMenuItem BUParserItem = new JRadioButtonMenuItem ("Use Bottom-Up Parser");
    BUParserItem.setMnemonic(KeyEvent.VK_B);
    BUParserItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          parserType = BUPARSE;
        }
      }
    );
    menu.add (BUParserItem);
    JRadioButtonMenuItem chartParserItem = new JRadioButtonMenuItem ("Use Top-Down Chart Parser");
    chartParserItem.setMnemonic(KeyEvent.VK_C);
    chartParserItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          parserType = CHARTPARSE;
        }
      }
    );
    menu.add (chartParserItem);
    ButtonGroup selectParserGroup = new ButtonGroup();
    selectParserGroup.add (recognizerItem);
    selectParserGroup.add (TDParserItem);
    selectParserGroup.add (BUParserItem);
    selectParserGroup.add (chartParserItem);

    final JCheckBoxMenuItem traceItem
      = new JCheckBoxMenuItem ("Parser Trace", parserTrace);
    traceItem.setMnemonic(KeyEvent.VK_A);
    traceItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          parserTrace = traceItem.getState();
        }
      }
    );
    menu.add (traceItem);
    /* JMenuItem traceOffItem = new JMenuItem ("Trace off");
    traceOffItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          parserTrace = false;
        }
      }
    );
    menu.add (traceOffItem);  */
    JMenuItem treeItem = new JMenuItem ("Draw Tree");
    treeItem.setMnemonic(KeyEvent.VK_E);
    treeItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          if (! (parses.isEmpty())) {
            int parseCount = parses.size();
            int parseNumber;
            if (parseCount == 1) {
              parseNumber = 0;
            } else {
              Integer[] parseNumberVector = new Integer[parseCount];
              for (int i=0; i<parseCount; i++) parseNumberVector[i]=new Integer(i+1);
              parseNumber = JOptionPane.showOptionDialog(null,
                "Draw which parse?", "Select a parse", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, parseNumberVector,
                new Integer (1));
            }
            ParseTreeNode parse = (ParseTreeNode) parses.elementAt(parseNumber);
            if (parse != null) {
              // new ParseTreeView ("Parse Tree " + (parseNumber+1), parse);
              new ParseView ("Parse Tree " + (parseNumber+1), parse.ann);
            }
          }
        }
      }
    );
    menu.add (treeItem);
    return menu;
  }
}
