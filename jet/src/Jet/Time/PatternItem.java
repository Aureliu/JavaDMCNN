// -*- tab-width: 4 -*-
package Jet.Time;

import java.util.List;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

/**
 *  a predefined pattern element for a time expression pattern.
 *
 *  @author  Akira Oda
 */

public abstract class PatternItem {

	/**
	 *  if tokens[offset] matches the Pattern Item, return a PatternMatchResult
	 *  containing the normalized value of the matched token along with
	 *  the span of the matched token, else <code>null</code>.
	 */

	public abstract PatternMatchResult match(Document doc, List<Annotation> tokens, int offset);
}
