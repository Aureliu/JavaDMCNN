// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.*;
import Jet.Tipster.*;

/**
 *  a set of pattern-action rules which are applied together when
 *  processing a document.
 */

public class PatternSet {

  Vector rules;
  public InternalPatternNode rootNode;

  /**
   *  creates an empty PatternSet (with no rules)
   */

  public PatternSet() {
    rules = new Vector();        // each is PatternRule
  }

  /**
   *  adds a rule to a PatternSet
   */

  public void addRule (PatternRule rule) {
    rules.addElement(rule);
  }

  /**
   *  converts the set of rules in this pattern set into
   *  a graph.  Once the rules have been converted to a graph,
   *  they can be applied to a document using the apply method.
   */

  public void makePatternGraph (PatternCollection collection) {
    Vector initialArcs = new Vector();
    Id id = new Id(1);

    for (int i = 0; i < rules.size(); i++) {
      PatternRule rule = (PatternRule) rules.get(i);
      PatternElement pe = collection.dereference(rule.patternName());
      if (pe == null) {
        System.out.println("Undefined pattern " + rule.patternName());
      } else {
        PatternGraph pg = pe.toGraph(id);
        Vector actions = rule.actions();
        PatternNode finalNode = new FinalPatternNode(new Id(id.value++), rule.patternName(), actions);
        //id.value++;
        pg.setOutEdges(finalNode);
        initialArcs.addAll(pg.inEdges);
      }
    }
    PatternArc[] initialArcArray = (PatternArc[]) initialArcs.toArray(new PatternArc[1]);
    rootNode = new InternalPatternNode(new Id(0), initialArcArray);
    // System.out.println (rootNode);
    return;
  }

  /**
   *  applies the rules in the PatternSet to the entire document.
   */

  public void apply (Document doc) {
    this.apply(doc, new Span(0, doc.length()));
  }

  /**
   *  applies the rules in the PatternSet to the specified span of the document.
   */

  public static int limit;

  public void apply (Document doc, Span span) {
    int position = span.start();
    int newPosition;
    limit = span.end();
    //  advance 'position' to start of first token
    while (doc.tokenAt(position) == null) {
      position++;
      if (position >= limit) return;
    }
    while (position < limit) {
      PatternApplication patap = new PatternApplication (doc, position);
      rootNode.eval(doc,position,new HashMap(),patap);
      if (patap.matchFound) {
        newPosition = patap.performActions();
      } else {
        newPosition = -1;
      }
      if (newPosition >= 0) {
        position = newPosition;
      } else {
        Annotation ann = doc.tokenAt(position);
        if (ann == null) return;
        position = ann.span().end();
      }
      // while ((position < limit) && Character.isWhitespace(doc.charAt(position)))
      // position++;
    }
  }
}
