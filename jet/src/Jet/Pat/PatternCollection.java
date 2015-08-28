// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.io.*;
import java.util.*;
import Jet.Lisp.*;
import Jet.Tipster.*;

public class PatternCollection {

  /* PATTERNS is a Hashtable from pattern names to patterns
     (PatternElements) */
  public Hashtable patterns;

  /* PATTERNSETS is a Hashtable from pattern set names to pattern sets
     (PatternSets) */
  public Hashtable patternSets;

  public Vector patternNames;
  public Vector patternSetNames;

  private PatternSet currentPatternSet;

  /**
   *  creates an empty PatternCollection
   */

  public PatternCollection () {
    patterns = new Hashtable();
    patternSets = new Hashtable();
    patternNames = new Vector();
    patternSetNames = new Vector();
  }

  /**
   *  reads a file of pattern statements using reader and
   *  builds (or augments) a PatternCollection in the form of
   *  patterns and rules.
   */

  public void readPatternCollection (Reader reader) throws IOException {
    currentPatternSet = null;
    LineNumberReader lnreader = new LineNumberReader (reader);
    StreamTokenizer tok = new StreamTokenizer(lnreader);
    while (true) {
      try {
        if(tok.nextToken() == StreamTokenizer.TT_EOF) return;
        readPatternStatement(tok);
        }
      catch (PatternSyntaxError pse) {
        int ln = lnreader.getLineNumber();
        System.out.println ("*** syntax error in pattern file, line " + ln);
        System.out.println (pse.toString());
        /*
        if (tok.ttype == StreamTokenizer.TT_WORD)
          System.out.println ("Current token = " + tok.sval);
        else
          System.out.println ("Current token = " + (char) tok.ttype);
        */
        System.out.println ("Current token = " + tok.toString());
        while (tok.nextToken() != ';') {
          if (tok.ttype == StreamTokenizer.TT_EOF) return;
        }
      }
    }
  }

  private void readPatternStatement (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    if (tok.ttype == StreamTokenizer.TT_WORD)
      if (tok.sval.equals("when")) readWhenStatement (tok);
      else if (tok.sval.equals("pattern")) readPatternSetStatement (tok);
      else readPatternDefinition (tok);
    else throw new PatternSyntaxError ();
  }

  private void readPatternDefinition (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    String patternName = tok.sval;
    if (tok.nextToken() != ':') throw new PatternSyntaxError (": expected");
    if (tok.nextToken() != '=') throw new PatternSyntaxError ("= expected");
    tok.nextToken();
    PatternElement pattern = readPatternAlternation (tok);
    if(tok.ttype != ';') throw new PatternSyntaxError ("; expected");
    patterns.put(patternName,pattern);
    patternNames.add(patternName);
    return;
  }

  /* for syntactic functions
     1. current token is first token of constituent
     2. on exit, current token is first token past constituent
  */

  // readPatternAlternation -- throws error if no options present
  private PatternElement readPatternAlternation (StreamTokenizer tok)
         throws IOException, PatternSyntaxError {
    Vector options = new Vector();
    options.addElement (readPatternSequence(tok));
    while (tok.ttype == '|') {
      tok.nextToken();
      options.addElement (readPatternSequence(tok));
    }
    if (options.size() == 1)
      return (PatternElement) options.get(0);
    else
      return new PatternAlternation (options);
  }

  // readPatternSequence -- throws error if no elements present
  private PatternElement readPatternSequence (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    Vector sequence = new Vector();
    PatternElement ape = readRepeatedPatternElement (tok);
    if (ape == null) throw new PatternSyntaxError ();
    do {
      sequence.addElement(ape);
      ape = readRepeatedPatternElement (tok);
    } while (ape != null);
    if (sequence.size() == 1)
      return (PatternElement) sequence.get(0);
    else
      return new PatternSequence (sequence);
  }

  private PatternElement readRepeatedPatternElement (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    PatternElement ape = readPatternElement (tok);
    if (ape == null) return null;
    char c = (char) tok.ttype;
    if (c == '?' || c == '*' || c == '+') {
      tok.nextToken();
      return new PatternRepetition(ape,c);
    } else {
      return ape;
    }
  }

  // matches:  string | annotation | pattern reference |
  //           assignment | ( alternation )
  //       exits with 'null' if not a pattern element

  private PatternElement readPatternElement (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    // Token pattern element
    if (tok.ttype == '"') {
      String stg = tok.sval;
      tok.nextToken();
      return new TokenStringPatternElement (stg);
    // annotation pattern element
    } else if (tok.ttype == '[') {
      if (tok.nextToken() != StreamTokenizer.TT_WORD)
          throw new PatternSyntaxError ();
      String type = tok.sval;
      FeatureSet fs = new FeatureSet (tok, true, ']');
      tok.nextToken();
      if (type.equals("integer")) {
        return new IntegerPatternElement (fs);
      } else if (type.equals("undefinedCap")) {
        return new UndefinedCapPatternElement(fs);
      } else {
        if (tok.ttype == ':') {
          if (tok.nextToken() ==  StreamTokenizer.TT_WORD &&
              Character.isUpperCase(tok.sval.charAt(0))) {
            String variable = tok.sval;
            tok.nextToken();
            return new AnnotationPatternElement (type,fs, new Variable(variable));
          } else {
            throw new PatternSyntaxError ("variable expected after :");
          }
        } else {
          return new AnnotationPatternElement (type,fs);
        }
      }
    } else if (tok.ttype == StreamTokenizer.TT_WORD) {
      if (Character.isUpperCase(tok.sval.charAt(0))) {
        String variable = tok.sval;
        if (tok.nextToken() != '=')
          throw new PatternSyntaxError ("= expected");
        PatternElement pe = null;
        if (tok.nextToken() == StreamTokenizer.TT_NUMBER) {
        	pe =  new AssignmentPatternElement (new Variable(variable),
        	                                   new Integer ( (int) tok.nval));
        } else if (tok.ttype == StreamTokenizer.TT_WORD) {
        	pe = new AssignmentPatternElement (new Variable(variable),
        	                                   tok.sval);
        } else {
          throw new PatternSyntaxError ("integer expected");
        }
        tok.nextToken();
        return pe;
      } else {
        String patternName = tok.sval;
        tok.nextToken();
        return new PatternReference (patternName, this);
      }
    } else if (tok.ttype == '(') {
      tok.nextToken();
      PatternElement pe = readPatternAlternation (tok);
      if (tok.ttype != ')') throw new PatternSyntaxError (") expected");
      if (tok.nextToken() == ':') {
        if (tok.nextToken() ==  StreamTokenizer.TT_WORD &&
            Character.isUpperCase(tok.sval.charAt(0))) {
          String variable = tok.sval;
          pe = new SpanBindingPatternElement(pe, new Variable(variable));
          tok.nextToken();
        } else {
          throw new PatternSyntaxError ("variable expected after :");
        }
      }
      return pe;
    } else return null;
  }

  private void readWhenStatement (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    if (tok.nextToken() != StreamTokenizer.TT_WORD)
        throw new PatternSyntaxError ("no pattern name in when statement");
    String patternName = tok.sval;
    Vector actions = new Vector();
    do {
      if (tok.nextToken() == StreamTokenizer.TT_WORD) {
        if (tok.sval.equals("print")) {
          actions.addElement (new PrintAction(tok));
        } else if (tok.sval.equals("write")) {
          actions.addElement (new WriteAction(tok));
        } else if (tok.sval.equals("add")) {
          actions.addElement (new NewAnnotationAction(tok));
        } else if (tok.sval.equals("addFeatures")) {
          actions.addElement (new AddFeaturesAction(tok));
        } else {
          throw new PatternSyntaxError ("unknown action " + tok.sval);
        }
      } else {
        throw new PatternSyntaxError ("unknown action " + tok.sval);
      }
    } while (tok.ttype == ',');
    if (tok.ttype != ';') throw new PatternSyntaxError ("; expected");
    if (currentPatternSet == null) {
      throw new PatternSyntaxError ("no pattern set defined");
    } else {
      currentPatternSet.addRule (new PatternRule(patternName,actions));
    }
    return;
  }

  private void readPatternSetStatement (StreamTokenizer tok)
          throws IOException, PatternSyntaxError {
    if (! (tok.nextToken() == StreamTokenizer.TT_WORD &&
           tok.sval.equals("set")))
          throw new PatternSyntaxError ("'set' expected");
    if (tok.nextToken() != StreamTokenizer.TT_WORD)
          throw new PatternSyntaxError ("pattern set name expected");
    String patternSetName = tok.sval;
    if (tok.nextToken() != ';') throw new PatternSyntaxError ("; expected");
    if (patternSets.containsKey(patternSetName)) {
      currentPatternSet = (PatternSet) patternSets.get(patternSetName);
    } else {
      currentPatternSet = new PatternSet();
      patternSets.put(patternSetName,currentPatternSet);
      patternSetNames.add(patternSetName);
    }
  }

  /**
   *  returns pattern named <I>patternName</I>, or <B>null</B> if no
   *  such pattern exists
   */

  public PatternElement getPattern (String patternName) {
    return (PatternElement) patterns.get(patternName);
  }

  /**
   *  returns pattern named <I>patternName</I>, or <B>null</B> if no
   *  such pattern exists (alias for getPattern)
   */

  public PatternElement dereference (String patternName) {
    return (PatternElement) patterns.get(patternName);
  }

  /**
   *  returns pattern set named <I>patternSetName</I>, or <B>null</B> if no
   *  such pattern set exists
   */

  public PatternSet getPatternSet (String patternSetName) {
    return (PatternSet) patternSets.get(patternSetName);
  }

  /**
   *  converts the set of rules in all pattern sets of this pattern
   *  collection into pattern graphs.  Once the rules have been converted
   *  to graphs, they can be applied to a document using the apply method.
   */

   public void makePatternGraph () {
    for (Enumeration pset = patternSets.elements(); pset.hasMoreElements();) {
      PatternSet set = (PatternSet) pset.nextElement();
      set.makePatternGraph(this);
    }
   }

   /**
    *  applies the rules in the named PatternSet to the document.
    *  If no such pattern set exists, no action is performed.
    */

   public void apply (String patternSetName, Document doc) {
     if (patternSets.containsKey(patternSetName)) {
       PatternSet set = (PatternSet) patternSets.get(patternSetName);
       set.apply(doc);
     }
   }

   /**
    *  applies the rules in the named PatternSet to the specified span.
    *  If no such pattern set exists, no action is performed.
    */

   public void apply (String patternSetName, Document doc, Span span) {
     if (patternSets.containsKey(patternSetName)) {
       PatternSet set = (PatternSet) patternSets.get(patternSetName);
       set.apply(doc,span);
     }
   }
}
