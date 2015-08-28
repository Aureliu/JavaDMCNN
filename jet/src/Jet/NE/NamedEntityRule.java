// -*- tab-width: 4 -*-
/**
 *
 */
package Jet.NE;

import java.util.Set;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;


public class NamedEntityRule {
	private MatchType type;

	private NamedEntityAttribute[] namedEntities;

	public NamedEntityRule(MatchType type, NamedEntityAttribute[] namedEntities) {
		this.type = type;
		this.namedEntities = namedEntities;
	}

	public boolean accept(Document doc, Annotation[] tokens, int n) {
		if (type == MatchType.ANY) {
			return true;
		}


		boolean result = false;
		Set<NamedEntityAttribute> attrs = (Set) tokens[n].get("categories");
		for (NamedEntityAttribute attr : namedEntities) {
			if (attrs.contains(attr)) {
				result = true;
				break;
			}
		}

		switch (type) {
		case NORMAL:
			return result;

		case NOT:
			return !result;

		default:
			// unreachable
			throw new InternalError();
		}
	}
}
