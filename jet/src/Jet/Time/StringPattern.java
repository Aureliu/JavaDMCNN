// -*- tab-width: 4 -*-
package Jet.Time;

import java.util.List;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class StringPattern extends PatternItem {
	private String str;

	public StringPattern(String str) {
		this.str = str;
	}

	public PatternMatchResult match(Document doc, List<Annotation> tokens, int offset) {
		Annotation token = tokens.get(offset);
		String tokenStr = doc.normalizedText(token);

		if (tokenStr.equals(str)) {
			return new PatternMatchResult(tokenStr, token.span());
		} else {
			return null;
		}
	}
}
