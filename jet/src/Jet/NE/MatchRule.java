// -*- tab-width: 4 -*-
/**
 *
 */
package Jet.NE;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public interface MatchRule {
	public boolean accept(Document doc, Annotation[] tokens, int n);
}
