// -*- tab-width: 4 -*-
package Jet.Time;

import java.util.List;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

/**
 *  A time expression pattern element for transformation patterns which
 *  matches a TIMEX2 annotation (as created by a basic time pattern).
 *
 *  @author  Akira Oda
 */

public class TimePattern extends PatternItem {
	private boolean isDuration;

	/**
	 *  creates a new TimePattern.
	 *
	 *  @param  isDuration  if true, this pattern matches a duration time
	 *                      expression (one whose VAL feature begins with a P);
	 *                      if false, matches any other time expression
	 */

	public TimePattern(boolean isDuration) {
		this.isDuration = isDuration;
	}

	/**
	 *  if the tokens beginning at token[offset] constitute a time expression,
	 *  return a PatternMatchResult incorporating that time expression and
	 *  its span;  otherwise return <code>null</code>.
	 */

	@Override
	public PatternMatchResult match(Document doc, List<Annotation> tokens,
			int offset) {
		int start = tokens.get(offset).start();
		List<Annotation> annotations = doc.annotationsAt(start, "TIMEX2");

		if (annotations == null || annotations.size() == 0) {
			return null;
		}

		Annotation time = annotations.get(0);
		String val = (String) time.get("VAL");
		if (val == null) {
			return null;
		}

		boolean isDuration = val.startsWith("P");
		if (this.isDuration && !isDuration) {
			return null;
		} else if (!this.isDuration && isDuration) {
			return null;
		}

		return new PatternMatchResult(time, time.span());
	}
}
