// -*- tab-width: 4 -*-
package Jet.Time;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class DayOfWeekPattern extends PatternItem {
	private static final Map<String, Integer> dayOfWeekNames;

	static {
		Map<String, Integer> m = new HashMap<String, Integer>();
		m.put("Monday", 1);
		m.put("Tuesday", 2);
		m.put("Wednesday", 3);
		m.put("Thursday", 4);
		m.put("Friday", 5);
		m.put("Saturday", 6);
		m.put("Sunday", 7);

		m.put("Mon", 1);
		m.put("Tue", 2);
		m.put("Wed", 3);
		m.put("Thu", 4);
		m.put("Fri", 5);
		m.put("Sat", 6);
		m.put("Sun", 7);

		dayOfWeekNames = m;
	}

	@Override
	public PatternMatchResult match(Document doc, List<Annotation> tokens,
			int offset) {
		String token = doc.normalizedText(tokens.get(offset));
		Integer value = dayOfWeekNames.get(token);

		if (value == null) {
			return null;
		}

		return new PatternMatchResult(value, tokens.get(offset).span());
	}

}
