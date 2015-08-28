// -*- tab-width: 4 -*-
package Jet.Time;

import java.util.List;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class NumberPattern extends PatternItem {
	public enum Ordinal {
		MUST, SHOULD, MUST_NOT
	}
	
	private int min;
	private int max;
	
	private Ordinal ordinal; 
	
	public NumberPattern(int min, int max, Ordinal ordinal) {
		this.min = min;
		this.max = max;
		this.ordinal = ordinal;
	}
	
	public NumberPattern(int min, int max) {
		this(min, max, Ordinal.SHOULD);
	}
	
	public NumberPattern(Ordinal ordinal) {
		this(Integer.MIN_VALUE, Integer.MAX_VALUE, ordinal);
	}

	public NumberPattern() {
		this(Integer.MIN_VALUE, Integer.MAX_VALUE, Ordinal.SHOULD);
	}

	@Override
	public PatternMatchResult match(Document doc, List<Annotation> tokens, int offset) {
		int start = tokens.get(offset).start();
		List<Annotation> numbers = doc.annotationsAt(start, "number");
		if (numbers == null || numbers.size() == 0) {
			return null;
		}

		Annotation number = numbers.get(0);
		Number value = (Number) number.get("value");
		Boolean ordinal = (Boolean) number.get("ordinal");
		if (ordinal == null) {
			ordinal = Boolean.FALSE;
		}

		if (min <= value.intValue() && value.intValue() <= max) {
			if (this.ordinal == Ordinal.MUST && !ordinal.booleanValue()) {
				return null;
			}
			if (this.ordinal == Ordinal.MUST_NOT && ordinal.booleanValue()) {
				return null;
			}
			return new PatternMatchResult(value, number.span());
		} else {
			return null;
		}
	}
}
