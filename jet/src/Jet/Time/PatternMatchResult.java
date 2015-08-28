// -*- tab-width: 4 -*-
package Jet.Time;

import Jet.Tipster.Span;

public class PatternMatchResult {
	public Object value;
	public Span span;

	public PatternMatchResult(Object value, Span span) {
		this.value = value;
		this.span = span;
	}
}

