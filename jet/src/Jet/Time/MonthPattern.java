// -*- tab-width: 4 -*-
package Jet.Time;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class MonthPattern extends PatternItem {
	private static Map<String, Integer> MONTH_NAMES;

	static {
		Map<String, Integer> m = new HashMap<String, Integer>();
		m.put("January", 1);
		m.put("February", 2);
		m.put("March", 3);
		m.put("April", 4);
		m.put("May", 5);
		m.put("June", 6);
		m.put("July", 7);
		m.put("August", 8);
		m.put("September", 9);
		m.put("October", 10);
		m.put("November", 11);
		m.put("December", 12);

		m.put("Jan", 1);
		m.put("Feb", 2);
		m.put("Mar", 3);
		m.put("Apr", 4);
		m.put("May", 5);
		m.put("Jun", 6);
		m.put("Jul", 7);
		m.put("Aug", 8);
		m.put("Sep", 9);
		m.put("Sept", 9);
		m.put("Oct", 10);
		m.put("Nov", 11);
		m.put("Dec", 12);

		MONTH_NAMES = m;
	}

	public MonthPattern() {
	}

	@Override
	public PatternMatchResult match(Document doc, List<Annotation> tokens, int offset) {
		String token = doc.normalizedText(tokens.get(offset));
		Number value = MONTH_NAMES.get(token);

		if (value != null) {
			// matched with month names
			return new PatternMatchResult(value, tokens.get(offset).span());
		}

		return null;
	}
}

