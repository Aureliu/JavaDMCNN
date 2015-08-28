// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Lex;

import java.util.*;
import Jet.Lisp.*;
import Jet.Tipster.*;

/**
 *  provides (static) data structures for storing and looking up
 *  word definitions.
 */

public class Lexicon {

  /* lexicon organization:
      lexicon consists of a set of lexiconEntries;
      lexiconIndex is a mapping from strings (the lower case of the first
        word of an entry) to a vector of lexicalEntries;
  */

  static Hashtable lexiconIndex = new Hashtable();

  /**
   *  clears the entire lexicon (remove all entries).
   */

  public static void clear () {
    lexiconIndex.clear ();
  }

  /**
    *  removes the definition (if any) for lexical item <I>words</I>.
    *  @return <B>true</B> if an entry is found for the item.
    */

  public static boolean clearEntry (String words[]) {
    String key = words[0].toLowerCase();
    if (lexiconIndex.containsKey(key)) {
      Vector entries = (Vector) lexiconIndex.get(key);
      for (int i = 0; i < entries.size(); i++) {
        LexicalEntry entry = (LexicalEntry) entries.get(i);
        if (entry.matches(words)) {
          entries.remove(i);
          return true;
        }
      }
    }
    return false;
  }

  /**
   *  adds <I>fs</I> to the lexicon as a definition of <I>words</I>
   */

	public static void addEntry (String words[], FeatureSet fs) {
		addEntry (words, fs, "constit");
	}

  public static void addEntry (String words[], FeatureSet fs, String type) {
    String key = words[0].toLowerCase();
    if (lexiconIndex.containsKey(key)) {
      Vector entries = (Vector) lexiconIndex.get(key);
      for (int i = 0; i < entries.size(); i++) {
        LexicalEntry entry = (LexicalEntry) entries.get(i);
        if (entry.matches(words)) {
          entry.addDefinition (fs);
          return;
        }
      }
      entries.addElement(new LexicalEntry(words, fs, type));
    } else {
      Vector entries = new Vector();
      entries.addElement(new LexicalEntry(words, fs, type));
      lexiconIndex.put(key,entries);
    }
  }

  /**
    * return an array of the definitions (FeatureSets) associated
    * with the lexical item <I>words</I>, or <B>null</B> if there are no
    * definitions associated with this lexical item.
    */

  public static FeatureSet[] lookUp (String words[]) {
    String key = words[0].toLowerCase();
    if (lexiconIndex.containsKey(key)) {
      Vector entries = (Vector) lexiconIndex.get(key);
      for (int i = 0; i < entries.size(); i++) {
        LexicalEntry entry = (LexicalEntry) entries.get(i);
        if (entry.matches(words))
          return entry.getDefinition();
      }
    }
    return null;
  }

  /** annotateWithDefinitions looks for the longest defined lexical item
    * consisting of the tokens starting at position <I>posn</I>;  if such
    * an item is found, then for each definition of this item, an
    * annotation of type <B>constit</B> is added to the item, with the
    * item's definition as its attributes.
    * @return the end position of this lexical item
    */

  public static int annotateWithDefinitions (Document doc, int posn) {
    int furthest = 0;
    FeatureSet[] definition = null;
    String type = null;
    Annotation ann = doc.tokenAt(posn);
    if (ann == null) return 0;
    String key = doc.text(ann).trim().toLowerCase();
    if (lexiconIndex.containsKey(key)) {
      Vector entries = (Vector) lexiconIndex.get(key);
      for (int i = 0; i < entries.size(); i++) {
        LexicalEntry entry = (LexicalEntry) entries.get(i);
        int newposn = entry.matches(doc,posn);
        if (newposn > 0) {
          if (newposn > furthest) {
            furthest = newposn;
            definition = entry.getDefinition();
            type = entry.type;
          }
        }
      }
    }
    if (definition != null) {
      for (int i = 0; i < definition.length; i++) {
        doc.annotate(type, new Span (posn,furthest),
                                new FeatureSet (definition[i]));
      }
    }
    return furthest;
  }

  public static void annotateWithDefinitions (Document doc, int start, int end) {
    int posn = start;
    int newposn;
    //  advance 'position' to start of first token
    while (doc.tokenAt(posn) == null) {
      posn++;
      if (posn >= end) return;
    }
    while (posn < end) {
      newposn = annotateWithDefinitions (doc, posn);
      if (newposn == 0) {
        Annotation ann = doc.tokenAt(posn);
        if (ann == null) return;
        posn = ann.span().end();
      } else {
        posn = newposn;
      }
      // while ((posn < doc.length()) && Character.isWhitespace(doc.charAt(posn))) posn++;
    }
  }
}
