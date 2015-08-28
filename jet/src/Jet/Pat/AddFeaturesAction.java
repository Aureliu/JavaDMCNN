// -*- tab-width: 4 -*-
package Jet.Pat;

import java.io.*;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.Console;
import java.util.HashMap;

/**
 *  the action (in a when statement) for adding features to an
 *  existing Annotation.
 */

public class AddFeaturesAction extends Action {

  FeatureSet features;
  Variable annotationVariable;

  public AddFeaturesAction(FeatureSet fs, Variable sv) {
    features = fs;
    annotationVariable = sv;
  }

  /**
   *  creates an AddFeaturesAction from the input processed by StreamTokenizer
   *  <I>tok</I>, which should have the form <BR>
   *  addfeatures [type feature=value feature=value ...] to annotationVariable
   */

  public AddFeaturesAction (StreamTokenizer tok)
    throws IOException, PatternSyntaxError {
    if (tok.nextToken() != '[')
      throw new PatternSyntaxError ("[ expected");
    features = new FeatureSet (tok, true, ']');
    if (tok.nextToken() == StreamTokenizer.TT_WORD &&
        tok.sval.equalsIgnoreCase("to")) {
      if (tok.nextToken() ==  StreamTokenizer.TT_WORD &&
          Character.isUpperCase(tok.sval.charAt(0))) {
        annotationVariable = new Variable(tok.sval);
        tok.nextToken();
      } else {
        throw new PatternSyntaxError ("variable expected after 'to'");
      }
    } else {
      throw new PatternSyntaxError ("'to' expected");
    }
  }

  /**
   *  performs the action, adding the specified Annotation.
   *  Returns the position of the end of the Annotation.
   */

  public int perform(Document doc, PatternApplication patap) {
    Annotation ann;
    HashMap bindings = patap.bestBindings;
    Object value = bindings.get(annotationVariable.name);
    if (value instanceof Annotation) {
        ann = (Annotation) value;
    } else {
        System.out.println ("Value of "+ annotationVariable.toString() +
                            " is not an annotation");
        return -1;
    }
    FeatureSet realFeatures = features.substitute(bindings);
    if (Pat.trace) Console.println ("Adding features " + realFeatures.toSGMLString()
    								+ " to " + ann);
    ann.attributes().putAll(realFeatures);
    return ann.span().end();
  }

  public String toString() {
      return "addFeatures [" + features.toSGMLString() + "] to " + annotationVariable.toString();
  }
}
