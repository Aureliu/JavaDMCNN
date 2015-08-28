// -*- tab-width: 4 -*-
//Title:        JET
//Version:
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Parser;

import java.io.*;
import java.util.Vector;
import java.util.Hashtable;
import Jet.Lisp.Literal;
import Jet.Pat.PatternSyntaxError;

/**
 *  a context-free grammar.
 */

public class Grammar {

  private Hashtable productions;
  private Hashtable productionsEndingIn;

  /**
   *  returns the definition of symbol <I>LHS</I> in the grammar, or
   *  <B>null</B> if <I>LHS</I> is not defined in the grammar.  The
   *  definition is returned as a vector of Productions.
   */

  public Vector getProductions (String LHS) {
    return (Vector) productions.get(LHS);
  }

  /**
   *  returns a Vector of all productions whose last element is <I>element</I>.
   */

  public Vector getProductionsEndingIn (Object element) {
    return (Vector) productionsEndingIn.get(element);
  }

  /**
   *  returns true if symbol s is defined (a non-terminal) in the grammar
   */

  public boolean defines (String s) {
    return productions.containsKey(s);
  }

  /**
   *  constructs a Grammar from the data on <I>reader</I>.
   *  The grammar consists of a sequence of definitions, each terminated
   *  by a semicolon.  Each definition has the form <BR>
   *  symbol := element element ... | element element ... | ... ;      <BR>
   *  where <I>element</I> is either a symbol or a string enclosed in
   *  double quotes (").  Note that each option (elements separated by "|")
   *  produces a separate production.
   */

  public Grammar(Reader reader) throws IOException {
    productions = new Hashtable();
    productionsEndingIn = new Hashtable();
    StreamTokenizer tok = new StreamTokenizer(reader);
    while (true) {
      try {
        if(tok.nextToken() == StreamTokenizer.TT_EOF) return;
        readGrammarRule(tok);
        }
      catch (PatternSyntaxError pse) {
        int ln = tok.lineno();
        System.out.println ("*** syntax error in grammar file, line " + ln);
        System.out.println (pse.toString());
        System.out.println ("Current token = " + tok.toString());
        while (tok.nextToken() != ';') {
          if (tok.ttype == StreamTokenizer.TT_EOF) return;
        }
      }
    }
  }

  private void readGrammarRule (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    String LHS = tok.sval.intern();
    if (tok.nextToken() != ':') throw new PatternSyntaxError (": expected");
    if (tok.nextToken() != '=') throw new PatternSyntaxError ("= expected");
    tok.nextToken();
    Vector prods = readRHS (LHS, tok);
    if(tok.ttype != ';') throw new PatternSyntaxError ("; expected");
    productions.put(LHS,prods);
    for (int i = 0;  i < prods.size(); i++) {
      Production prod = (Production) prods.elementAt(i);
      Object lastElement = prod.rhs().lastElement();
      if (!(productionsEndingIn.containsKey(lastElement))) {
        productionsEndingIn.put(lastElement,new Vector());
      }
      ((Vector) productionsEndingIn.get(lastElement)).addElement(prod);
      }
    return;
  }

  private Vector readRHS (String LHS, StreamTokenizer tok)
         throws IOException, PatternSyntaxError {
    Vector prods = new Vector();
    prods.addElement (new Production(LHS, readOption(tok)));
    while (tok.ttype == '|') {
      tok.nextToken();
      prods.addElement (new Production(LHS, readOption(tok)));
    }
    return prods;
  }

  // readOption -- throws error if no elements present
  private Vector readOption (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    Vector elements = new Vector();
    Object ge = readGrammarElement (tok);
    if (ge == null) throw new PatternSyntaxError ();
    do {
      elements.addElement(ge);
      ge = readGrammarElement (tok);
    } while (ge != null);
    return elements;
  }

  private Object readGrammarElement (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    if (tok.ttype == '"') {
      Literal lit = new Literal(tok.sval.intern());
      tok.nextToken();
      return lit;
    } else if  (tok.ttype == StreamTokenizer.TT_WORD) {
      String sym = tok.sval.intern();
      tok.nextToken();
      return sym;
    } else {
      return null;
    }
  }
}
