// -*- tab-width: 4 -*-
/**
 *
 */
package Jet.NE;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class ClassRule {
	private static Pattern hourMinutePattern = Pattern
			.compile("(\\d{1,2}):(\\d{2})");

	private enum SpecialType {
		CAPITAL {
			public boolean accept(Document doc, Annotation[] tokens, int pos,
					ClassHierarchyResolver resolver) {
				return isCapital(doc, tokens, pos);
			}
		},
		DCAPITAL {
			public boolean accept(Document doc, Annotation[] tokens, int pos,
					ClassHierarchyResolver resolver) {
				return !isCapital(doc, tokens, pos);
			}
		},
		YEAR {
			public boolean accept(Document doc, Annotation[] tokens, int pos,
					ClassHierarchyResolver resolver) {
				String text = doc.normalizedText(tokens[pos]);
				return isYear(text);
			}
		},
		HOUR_MINUTE {
			public boolean accept(Document doc, Annotation[] tokens, int pos,
					ClassHierarchyResolver resolver) {
				String text = doc.normalizedText(tokens[pos]);
				return isHourMinute(text);
			}
		},
		TWO_DIGIT_YEAR {
			public boolean accept(Document doc, Annotation[] tokens, int pos,
					ClassHierarchyResolver resolver) {
				String text = doc.normalizedText(tokens[pos]);
				return isTwoDigitYear(text);
			}
		};

		abstract boolean accept(Document doc, Annotation[] tokens, int pos,
				ClassHierarchyResolver resolver);
	}

	private static final int YEAR_MIN = 1000;

	private static final int YEAR_MAX = 2030;

	private MatchType type;

	private String[] categories;

	public ClassRule(MatchType type, String[] categories) {
		this.type = type;
		this.categories = categories;
	}

	public boolean accept(Document doc, Annotation[] tokens, int n,
			ClassHierarchyResolver resolver) {
		if (type == MatchType.ANY) {
			return true;
		}

		if (type == MatchType.SPECIAL) {
			if (categories.length != 1) {
				throw new UnsupportedOperationException();
			}

			try {
				SpecialType specialType = SpecialType.valueOf(categories[0]);
				return specialType.accept(doc, tokens, n, resolver);
			} catch (IllegalArgumentException ex) {
				String message = categories[0] + " is not supported.";
				throw new UnsupportedOperationException(message);
			}
		}

		boolean matched = false;
		Set<NamedEntityAttribute> neList = (Set<NamedEntityAttribute>) tokens[n]
				.get("categories");
		LOOP_MATCH: for (NamedEntityAttribute ne : neList) {
			for (String category : categories) {
				if (resolver.isSubClassOf(ne.getCategory(), category)) {
					matched = true;
					break LOOP_MATCH;
				}
			}
		}

		switch (type) {
		case NORMAL:
			return matched;

		case NOT:
			return !matched;
		}
		
		throw new InternalError();
	}

	/**
	 * Determines if the given word is capital word.
	 *
	 * @param tokens
	 * @param n
	 * @return
	 */
	static boolean isCapital(Document doc, Annotation[] tokens, int n) {
		String text = doc.text(tokens[n]).trim();

		if (text.length() == 0 || !Character.isUpperCase(text.charAt(0))) {
			return false;
		}

		String pos = (String) tokens[n].get("pos");
		if (pos != null && (pos.equals("NNP") || pos.equals("NNPS"))) {
			return true;
		} else if (n == 0) {
			// if beginning of sentence
			return false;
		} else {
			String before = doc.text(tokens[n - 1]).trim();
			if (before.equals("\"") || before.equals("``")
					|| before.equals("`")) {
				// if beginning of sentence
				return false;
			}
		}

		return true;
	}

	static boolean isYear(String text) {
		if (text.length() != 4) {
			return false;
		}

		try {
			int n = Integer.parseInt(text);
			if (n < YEAR_MIN || n > YEAR_MAX) {
				return false;
			}
		} catch (NumberFormatException ex) {
			return false;
		}

		return true;
	}

	static boolean isTwoDigitYear(String text) {
		if (text.length() != 2) {
			return false;
		}

		return Character.isDigit(text.charAt(0))
				&& Character.isDigit(text.charAt(1));
	}

	static boolean isHourMinute(String text) {
		Matcher matcher = hourMinutePattern.matcher(text);
		if (!matcher.matches()) {
			return false;
		}

		int hour = Integer.parseInt(matcher.group(1));
		int minute = Integer.parseInt(matcher.group(2));
		if (hour < 0 || hour > 24) {
			return false;
		}
		if (minute < 0 || minute > 60) {
			return false;
		}

		return true;
	}
}
