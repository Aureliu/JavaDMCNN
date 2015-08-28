// -*- tab-width: 4 -*-
package Jet.Time;

import gnu.trove.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

import Jet.Lisp.FeatureSet;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

/**
 * NumberAnnotator provides annotation number expression and normalizing value.
 * 
 * @author Akira ODA
 */
public class NumberAnnotator {
	private static TObjectIntHashMap numberNames;

	private static TObjectIntHashMap ordinalNumberNames = new TObjectIntHashMap();

	static {
		TObjectIntHashMap m = new TObjectIntHashMap();

		m.put("zero", 0);
		m.put("one", 1);
		m.put("two", 2);
		m.put("three", 3);
		m.put("four", 4);
		m.put("five", 5);
		m.put("six", 6);
		m.put("seven", 7);
		m.put("eight", 8);
		m.put("nine", 9);
		m.put("ten", 10);
		m.put("eleven", 11);
		m.put("twelve", 12);
		m.put("thirteen", 13);
		m.put("fourteen", 14);
		m.put("fifteen", 15);
		m.put("sixteen", 16);
		m.put("seventeen", 17);
		m.put("eighteen", 18);
		m.put("nineteen", 19);
		m.put("twenty", 20);
		m.put("thirty", 30);
		m.put("forty", 40);
		m.put("fifty", 50);
		m.put("sixty", 60);
		m.put("seventy", 70);
		m.put("eighty", 80);
		m.put("ninety", 90);
		m.put("hundred", 100);
		m.put("thousand", 1000);
		m.put("million", 100000);

		numberNames = m;

		TObjectIntHashMap n = new TObjectIntHashMap();
		n.put("first", 1);
		n.put("second", 2);
		n.put("third", 3);
		n.put("fifth", 5);
		n.put("eighth", 8);
		n.put("twelfth", 12);
		ordinalNumberNames = n;
	}

	private static class NumberInformation {
		private boolean ordinal;

		private int value;

		private String token;

		public NumberInformation(String token, int value, boolean ordinal) {
			this.token = token;
			this.value = value;
			this.ordinal = ordinal;
		}

		public boolean isOrdinal() {
			return ordinal;
		}

		public void setOrdinal(boolean ordinal) {
			this.ordinal = ordinal;
		}

		public String getToken() {
			return token;
		}

		public void setToken(String token) {
			this.token = token;
		}

		public int getValue() {
			return value;
		}

		public void setValue(int value) {
			this.value = value;
		}
	}

	/**
	 * Annotates number expression and normalize value.
	 * 
	 * @param doc
	 *            <code>Document<code> object should be annotated.
	 */
	public void annotate(Document doc) {
		Span span = new Span(0, doc.length());
		annotate(doc, span);
	}

	/**
	 * Annotates number expression and normalize value.
	 * 
	 * @param doc
	 * @param span
	 *            <code>Document<code> object should be annotated.
	 */
	public void annotate(Document doc, Span span) {
		Vector<Annotation> v = doc.annotationsOfType("token", span);
		if (v == null)
			return;
		int size = v.size();

		List<String> expressions = new ArrayList<String>();
		List<NumberInformation> numbers = new ArrayList();
		int start = span.start();

		for (int i = 0; i < size; i++) {
			Annotation a = v.get(i);
			String token = doc.text(a).trim();

			if (token.equals("-")) {
				continue;
			}

			NumberInformation num = null;

			if (a.get("intvalue") != null) {
				Object iv = a.get("intvalue");
				Integer ii;
				if (iv instanceof Integer) {
					ii = (Integer) iv;
				} else if (iv instanceof String) {
					try {
						ii = new Integer((String) iv);
					} catch (NumberFormatException e) {
						System.out.println("*** invalid intvalue " + a);
						ii = new Integer(0);
					}
				} else {
					System.out.println("*** invalid intvalue " + a);
					ii = new Integer(0);
				}
				FeatureSet attrs = new FeatureSet("value", ii);
				doc.annotate("number", a.span(), attrs);
			} else {
				num = resolveNumber(token);
			}

			if (num != null) {
				if (numbers.isEmpty()) {
					// number expression start
					start = a.start();
					expressions.clear();
				}

				numbers.add(num);
				expressions.add(token);
			} else {
				if (!numbers.isEmpty()) {
					if (!checkPhoneNumber(expressions)) {
						// number expression end
						int value = calcNumber(numbers);
						int end = ((Annotation) v.get(i - 1)).end();

						FeatureSet attrs = new FeatureSet();
						attrs.put("value", new Integer(value));
						if (numbers.get(numbers.size() - 1).isOrdinal()) {
							attrs.put("ordinal", true);
						}
						doc.annotate("number", new Span(start, end), attrs);
					}
					numbers.clear();
				}
			}
		}

		if (!numbers.isEmpty()) {
			int value = calcNumber(numbers);
			int end = ((Annotation) v.lastElement()).end();
			FeatureSet attrs = new FeatureSet();
			attrs.put("value", new Integer(value));
			if (numbers.get(numbers.size() - 1).isOrdinal()) {
				attrs.put("ordinal", true);
			}
			doc.annotate("number", new Span(start, end), attrs);
		}
	}

	private int calcNumber(List<NumberInformation> v) {
		int result = 0;
		int accum = 0;
		for (NumberInformation n : v) {
			int num = n.getValue();
			if (num >= 1000) {
				result += (accum > 0 ? accum : 1) * num;
				accum = 0;
			} else if (num == 100) {
				accum = (accum > 0 ? accum : 1) * num;
			} else {
				accum += num;
			}
		}

		result += accum;
		return result;
	}

	private NumberInformation resolveNumber(String exp) {
		String key = exp.toLowerCase().trim();
		if (key.endsWith(".")) {
			// strip ends with period
			key = key.substring(0, key.length() - 1);
		}
		Pattern numPattern = Pattern.compile("[+-]?\\d*(?:1st|2nd|3rd|[04-9]th)",
				Pattern.CASE_INSENSITIVE);

		NumberInformation number;

		if (numberNames.containsKey(key)) {
			return new NumberInformation(exp, numberNames.get(key), false);
		} else if ((number = resolveOrdinalNumber(exp)) != null) {
			return number;
		} else if (numPattern.matcher(key).matches()) {
			int val = Integer.parseInt(key.substring(0, key.length() - 2));
			return new NumberInformation(exp, val, true);
		} else {
			try {
				int val = Integer.parseInt(key);
				return new NumberInformation(exp, val, false);
			} catch (NumberFormatException ex) {
				return null;
			}
		}
	}

	/**
	 * Returns expressions is presented phone number.
	 * 
	 * @param expressions
	 *            list of expression tokens
	 * @return true if expressions is phone number otherwise false.
	 */
	private boolean checkPhoneNumber(List<String> expressions) {
		if (expressions.size() == 1) {
			return false;
		}

		Pattern numRegex = Pattern.compile("\\d+");
		for (String expression : expressions) {
			if (!numRegex.matcher(expression).matches()) {
				return false;
			}
		}

		return true;
	}

	private NumberInformation resolveOrdinalNumber(String exp) {
		String key = exp.toLowerCase();
		
		if (ordinalNumberNames.containsKey(key)) {
			return new NumberInformation(exp, ordinalNumberNames.get(key), true);
		}
		
		if (exp.endsWith("th")) {
			String stem = exp.substring(0, exp.length() - 2);
			if (numberNames.contains(stem)) {
				int val = numberNames.get(stem);
				return new NumberInformation(stem, val, true);
			}
		}

		return null;
	}
}
