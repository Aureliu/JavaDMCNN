// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000, 2001
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.*;
import Jet.Tipster.*;
import Jet.Console;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 *  records information about the matching of a pattern graph against a
 *  segment of a Document.
 */

public class PatternApplication {

  /**
   *  <B>true</B> if a match has been found between the pattern graph and
   *  the Document.
   */

  public boolean matchFound;

  /**
   *  the Document being matched.
   */

  Document document;

  /**
   *  the position in the Document where the matching starts.
   */

  public int startPosition;

  /**
   *  if a match has been found, the document position matching the end of
   *  the pattern.  If several matches were found, the best (furthest) end
   *  position.
   */

  public int bestPosition;

  /**
   *  the name of the best pattern match found so far.
   */

  public String bestPatternName;

  /**
   *  the bindings of variables to values associated with the best pattern
   *  match found so far.
   */

  public HashMap bestBindings;

  /**
   *  the sequence of actions associated with the best pattern match found
   *  so far.
   */

  public Vector bestActions;

  /**
   *  if <B>true</B>, write a trace message to the console whenever a
   *  pattern is successfully matched.
   */

  public static boolean patternMatchTrace = false;

  /**
   *  if <B>true</B>, write a trace message to the console whenever a
   *  the actions associated with a pattern are applied.
   */

  public static boolean patternApplyTrace = false;

  /**
   *  create a PatternApplication at the beginning of the pattern matching
   *  process for a pattern graph.
   *
   *  @param doc      the Document being matched
   *  @param start    the position in the document where matching will start
   */

  private static Hashtable matchTracedPatterns = new Hashtable();
  private static Hashtable applyTracedPatterns = new Hashtable();
  private static JCheckBoxMenuItem item;
  private static String patternName;
  private static String patternSetName;
  private static JMenu matchSubmenu;
  private static JMenu applySubmenu;
  private static int numOfPatterns;
  private static int numOfApplyPatterns;
  private static PatternCollection pc;
  private static PatternSet ps;
  private static PatternRule pr;

  public PatternApplication(Document doc, int start) {
    matchFound = false;
    document = doc;
    startPosition = start;
    bestPosition = -1;
  }

  /**
   *  invoked for a successful match of a pattern.  If the end position,
   *  <I>position</I>, is further than any prior match, record the new
   *  position, variable bindings, and actions.  Note that the actions are
   *  not performed immediately, but are saved in case there are subsequent
   *  better (longer) matchings of this pattern graph.
   *
   *  @param position   position in document reached by end of pattern
   *  @param bindings   variable bindings for this pattern
   *  @param actions    actions to be performed if this is best pattern match
   */

  public void recordMatch (int position, String patternName, HashMap bindings,
                           Vector actions) {
    Object name = matchTracedPatterns.get(patternName);
    if (name != null && name.equals("true")) {
      Console.println ("Matched pattern " + patternName + " over "
                       + document.text(new Span(startPosition, position)));
      if (Console.pv != null) {
        Console.pv.addMatchedPattern(patternName);
        Console.pv.refresh();
      }
    }
    if (position > bestPosition) {
      matchFound = true;
      bestPosition = position;
      bestPatternName = patternName;
      bestBindings = bindings;
      bestActions = actions;
    }
  }

  /**
   *  perform the actions associated with this pattern application.  If no
   *  match has been found, no actions are performed;  otherwise, perform
   *  the actions associated with the best (longest) pattern match.  This
   *  method is invoked when all possible matches of the pattern graph
   *  against the document, starting at the current position, have been
   *  exhausted.
   *  @return  the last position in the document matched by the pattern;
   *           -1 if no match was found.
   */

  public int performActions () {
    if (matchFound) {
      int furthestPositionAnnotated = -1;
      Object name = applyTracedPatterns.get(bestPatternName);
      if (name != null && name.equals("true")) {
        Console.println ("Applying pattern " + bestPatternName + " over "
                         + document.text(new Span(startPosition, bestPosition)));
        if (Console.pv != null) {
          // Console.pv.clearMatchedPatterns();
          Console.pv.addAppliedPattern(bestPatternName);
          Console.pv.refresh();
        }
      }
      for (int i = 0; i < bestActions.size(); i++) {
        Action act = (Action) bestActions.get(i);
        furthestPositionAnnotated =
          Math.max(furthestPositionAnnotated, act.perform(document, this));
      }
      return furthestPositionAnnotated;
    } else {
      return -1;
    }
  }

  // ----------  p a t t e r n   m e n u ----------

  /**
   *  Returns a menu for controlling the pattern matcher (part of the Console menu
   *  bar).  Includes menu items for patternMatchTrace and patternApplyTrace.
   *  DEPRECATED as of 4/25/2001. Use matchSubmenu() and applySubMenu() instead.
   */

  public static JMenu patternMenu () {
    JMenu menu = new JMenu("Patterns");
    menu.setMnemonic(KeyEvent.VK_P);
    final JCheckBoxMenuItem matchTraceItem
      = new JCheckBoxMenuItem ("Pattern Match Trace", patternMatchTrace);
    matchTraceItem.setMnemonic(KeyEvent.VK_M);
    matchTraceItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          patternMatchTrace = matchTraceItem.getState();
        }
      }
    );
    menu.add (matchTraceItem);
    final JCheckBoxMenuItem applyTraceItem
      = new JCheckBoxMenuItem ("Pattern Apply Trace", patternApplyTrace);
    applyTraceItem.setMnemonic(KeyEvent.VK_A);
    applyTraceItem.addActionListener(
      new ActionListener() {
        public void actionPerformed (ActionEvent e) {
          patternApplyTrace = applyTraceItem.getState();
        }
      }
    );
    menu.add (applyTraceItem);
    return menu;
  }

  /**
   * Returns the "Pattern Match Trace" submenu used in the Console's
   * "Patterns" menu.
   */
  public static JMenu matchSubmenu () {
    pc = Jet.JetTest.pc;
    numOfPatterns = pc.patternNames.size();

    matchSubmenu = new JMenu("Pattern Match Trace");
    matchSubmenu.setMnemonic(KeyEvent.VK_M);

    item = new JCheckBoxMenuItem("All");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (((JCheckBoxMenuItem) e.getSource()).getState())
          for (int i = 2; i < numOfPatterns + 2; i++) {
            JCheckBoxMenuItem jcbmi = (JCheckBoxMenuItem) matchSubmenu.getItem(i);
            jcbmi.setState(true);
            matchTracedPatterns.put(jcbmi.getText(), "true");
          }
        else
          for (int i = 2; i < numOfPatterns + 2; i++) {
            JCheckBoxMenuItem jcbmi = (JCheckBoxMenuItem) matchSubmenu.getItem(i);
            jcbmi.setState(false);
            matchTracedPatterns.put(jcbmi.getText(), "false");
          }
      }
    });
    matchSubmenu.add(item);
    matchSubmenu.addSeparator();

    for (int i = 0; i < numOfPatterns; i++) {
      patternName = (String) pc.patternNames.get(i);
      item = new JCheckBoxMenuItem(patternName);
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (((JCheckBoxMenuItem) e.getSource()).getState())
            matchTracedPatterns.put(e.getActionCommand(), "true");
          else {
            matchTracedPatterns.put(e.getActionCommand(), "false");
            JCheckBoxMenuItem all = (JCheckBoxMenuItem) matchSubmenu.getItem(0);
            all.setState(false);
          }
        }
      });
      matchSubmenu.add(item);
    }

    return matchSubmenu;
  }

  /**
   * Returns the "Pattern Apply Trace" submenu used in the Console's
   * "Patterns" menu.
   */
  public static JMenu applySubmenu() {
    pc = Jet.JetTest.pc;
    numOfApplyPatterns = 0;

    applySubmenu = new JMenu("Pattern Apply Trace");
    applySubmenu.setMnemonic(KeyEvent.VK_A);

    item = new JCheckBoxMenuItem("All");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (((JCheckBoxMenuItem) e.getSource()).getState())
          for (int i = 2; i < numOfApplyPatterns + 2; i++) {
            JCheckBoxMenuItem jcbmi = (JCheckBoxMenuItem) applySubmenu.getItem(i);
            jcbmi.setState(true);
            applyTracedPatterns.put(jcbmi.getText(), "true");
          }
        else
          for (int i = 2; i < numOfApplyPatterns + 2; i++) {
            JCheckBoxMenuItem jcbmi = (JCheckBoxMenuItem) applySubmenu.getItem(i);
            jcbmi.setState(false);
            applyTracedPatterns.put(jcbmi.getText(), "false");
          }
      }
    });
    applySubmenu.add(item);
    applySubmenu.addSeparator();

    for (int i = 0; i < pc.patternSetNames.size(); i++) {
      patternSetName = (String) pc.patternSetNames.get(i);
      ps = (PatternSet) pc.patternSets.get(patternSetName);
      for (int j = 0; j < ps.rules.size(); j++) {
        pr = (PatternRule) ps.rules.get(j);
        item = new JCheckBoxMenuItem(pr.patternName);
        item.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (((JCheckBoxMenuItem) e.getSource()).getState())
              applyTracedPatterns.put(e.getActionCommand(), "true");
            else {
              applyTracedPatterns.put(e.getActionCommand(), "false");
              JCheckBoxMenuItem all = (JCheckBoxMenuItem) applySubmenu.getItem(0);
              all.setState(false);
            }
          }
        });
        applySubmenu.add(item);
      }
      numOfApplyPatterns += ps.rules.size();
    }

    return applySubmenu;
  }
}
