// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.*;
import java.io.*;
import Jet.Lisp.*;
import Jet.Tipster.*;

/**
 *  contains static procedures used in pattern matching.
 */

public class Pat {

/**
 *  if true, write a message to the Console whenever a pattern
 *  adds an annotation to a Document.
 */

public static boolean trace = false;

/**
 *  determines whether annotations <I>ann1</I> and <I>ann2</I> can be
 *  matched (unified), consistent with variable bindings <I>bindings</I>.
 *  Two annotations can be matched if they have the same type and their
 *  features can be matched.
 *  Both <I>ann1</I> and <I>ann2</I> may include variables, so the
 *  matching process may cause more variables to be bound.
 *  @return    if the annotations can be unified, a HashMap with
 *             all (existing and new) bindings;  otherwise <B>null</B>.
 */

public static HashMap matchAnnotations (Annotation ann1, Annotation ann2,
                                 HashMap bindings) {
  if (ann1.type() == ann2.type())
    return matchFS (ann1.attributes(), ann2.attributes(), bindings);
    else return null;
  }

/**
 *  determines whether feature sets <I>fs1</I> and <I>fs2</I> can be
 *  matched (unified), consistent with variable bindings <I>bindings</I>.
 *  Both <I>fs1</I> and <I>fs2</I> may include variables, so the
 *  matching process may cause more variables to be bound.
 *  @return    if the feature sets can be unified, a HashMap with
 *             all (existing and new) bindings;  otherwise <B>null</B>.
 */

public static HashMap matchFS (FeatureSet fs1, FeatureSet fs2,
                                  HashMap bindings) {
  Enumeration e = fs2.keys();
  while (e.hasMoreElements()) {
    String key = (String) e.nextElement();
    Object value2 = fs2.get(key);
    Object value1 = fs1.get(key);
    if (value2 == null) {
      if (value1 != null) return null;
    } else if (value2 instanceof Variable) {
      String sym = ((Variable)value2).name;
      if (bindings.containsKey(sym)) { // if variable is bound
        value2 = bindings.get(sym);
        if (!value2.equals(value1)) return null;
      } else {
      bindings = (HashMap) bindings.clone();
      bindings.put(sym,value1);
      }
    } else if (value2 instanceof FeatureTest) {
      FeatureTest ft = (FeatureTest)value2;
      if (!ft.apply(value1)) return null;
    } else if (value1 == null) {
      return null;
    } else if (value2 instanceof FeatureSet) {
      if (value1 instanceof FeatureSet) {
        bindings = matchFS ((FeatureSet)value1, (FeatureSet)value2, bindings);
        if (bindings == null) return null;
      }
      else return null;
    } else if (!value2.equals(value1)) {
      return null;
    }
  }
  return bindings;
}
}


