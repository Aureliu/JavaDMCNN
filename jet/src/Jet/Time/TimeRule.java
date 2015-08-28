// -*- tab-width: 4 -*-
package Jet.Time;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

public abstract class TimeRule {
	private PatternItem[] patterns;
	private TimeAnnotator timeAnnotator;
	private Map params;

	public abstract void apply(Document doc, List<Object> values, Span span, DateTime ref);

	public void setTimeAnnotator(TimeAnnotator timeAnnotator) {
		this.timeAnnotator = timeAnnotator;
	}

	public TimeAnnotator getTimeAnnotator() {
		return timeAnnotator;
	}

	public void setPatternItems(PatternItem[] patterns) {
		this.patterns = patterns;
	}

	public PatternItem[] getPatternItems() {
		return patterns;
	}

	public void setParameters(Map params) {
		this.params = params;
	}

	public Map getParameters() {
		return params;
	}

	/**
	 *  matches the pattern portion of the current TimeRule against the sequence
	 *  of <code>tokens</code> in <code>doc</doc> starting with <code>tokens[offset]</code>.
	 *  If the match is successful, adds to <code>values</code> the values of
	 *  the items matching the pattern elements.
	 *
	 *  @return if the match is successful, the span of tokens matched,
	 *          otherwise <code>null</code>
	 */

	public Span matches(Document doc, List<Annotation> tokens, int offset, DateTime ref, List<Object> values) {
		int pos = offset;
		int start = tokens.get(offset).start();
		int end = start;
		for (int i = 0; i < patterns.length; i++) {
			if (pos >= tokens.size()) {
				return null;
			}
			PatternMatchResult result = patterns[i].match(doc, tokens, pos);
			if (result == null) {
				return null;
			}

			values.add(result.value);
			pos = nextOffset(tokens, pos, result.span);
			end = result.span.end();
		}

		return new Span(start, end);
	}

	protected int nextOffset(List<Annotation> tokens, int offset, Span span) {
		int i = offset;
		while (i < tokens.size()) {
			if (tokens.get(i).start() >= span.end()) {
				break;
			}
			i++;
		}

		return i;
	}

	protected String assignValues(String value, List<Object> values) {
		Pattern regex = Pattern.compile("\\((\\d+)\\)");
		Matcher matcher = regex.matcher(value);

		StringBuilder buffer = new StringBuilder();
		int start = 0;
		while (matcher.find()) {
			int offset = Integer.parseInt(matcher.group(1)) - 1;
			buffer.append(value, start, matcher.start());
			buffer.append(values.get(offset));
			start = matcher.end();
		}

		buffer.append(value, start, value.length());

		return buffer.toString();
	}
}
