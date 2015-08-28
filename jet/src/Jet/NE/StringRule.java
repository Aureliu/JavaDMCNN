// -*- tab-width: 4 -*-
/**
 *
 */
package Jet.NE;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;


public class StringRule {
	private MatchType type;

	private String[] strings;

	public StringRule(MatchType type, String[] strings) {
		this.type = type;
		this.strings = new String[strings.length];
		for (int i = 0; i < strings.length; i++) {
			this.strings[i] = strings[i].intern();
		}
	}

	public boolean accept(Document doc, Annotation[] tokens, int n) {
		if (type == MatchType.ANY) {
			return true;
		}

		boolean matched = false;
		String text = doc.normalizedText(tokens[n]);

		for (String string : strings) {
			if (string.equals(text)) {
				matched = true;
				break;
			}
		}

		switch (type) {
		case NORMAL:
			return matched;

		case NOT:
			return !matched;

		default:
			// unreachable
			throw new InternalError();
		}
	}
}
