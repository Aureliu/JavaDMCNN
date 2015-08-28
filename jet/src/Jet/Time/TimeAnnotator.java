// -*- tab-width: 4 -*-
package Jet.Time;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ho.yaml.Yaml;
import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.IllegalFieldValueException;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;
import Jet.Util.IOUtils;

/**
 * Rule-driven time expression annotator. The rules are encoded in YAML format
 * and consist of the main rules (marked "mainRules") and the transform rules
 * (marked "transformRules").
 * 
 * @author Akira Oda
 */

public class TimeAnnotator {
	private List<TimeRule> mainRules;

	private List<TimeRule> transformRules;

	private Map config;

	private int early_month = 4;

	private int late_month = 9;
	
	private int min_year = 1000;
	
	private int max_year = 9999;

	public TimeAnnotator() {
	}

	public TimeAnnotator(String ruleFilename) throws IOException {
		this(new File(ruleFilename));
	}

	public TimeAnnotator(File ruleFile) throws IOException {
		FileInputStream in = null;
		try {
			in = new FileInputStream(ruleFile);
			load(in);
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	/**
	 * annotate the time expressions in 'span' with TIMEX2 annotations.
	 * 
	 * @param doc
	 *            the document to be annotated
	 * @param span
	 *            the span of doc to be annotated
	 * @param ref
	 *            the reference time -- the time the text was written
	 */

	public void annotate(Document doc, Span span, DateTime ref) {
		applyRules(doc, span, ref, mainRules);
		if (transformRules != null) {
			applyRules(doc, span, ref, transformRules);
		}
	}

	private void applyRules(Document doc, Span span, DateTime ref, List<TimeRule> rules) {
		List<Annotation> tokens = doc.annotationsOfType("token", span);
		if (tokens == null)
			return;
		int offset = 0;

		while (offset < tokens.size()) {
			Span resultSpan = null;
			for (TimeRule rule : rules) {
				List<Object> values = new ArrayList<Object>();
				resultSpan = rule.matches(doc, tokens, offset, ref, values);
				if (resultSpan != null) {
					try {
						rule.apply(doc, values, resultSpan, ref);
						break;
					} catch (IllegalFieldValueException e) {
						// skip when IllegalFIeldValueException is thrown
					} catch (IllegalArgumentException e) {
						System.err.println ("TimeAnnotator.applyRules:  " + e);
					}						
				}
			}

			if (resultSpan != null) {
				offset = nextOffset(tokens, offset, resultSpan);
			} else {
				offset++;
			}
		}
	}

	/**
	 * load the time expression rules from 'in'. These rules should be in YAML
	 * format.
	 */
	public void load(InputStream in) throws IOException {
		mainRules = null;

		Map root = (Map) Yaml.load(in);
		List rules = (List) root.get("rules");
		config = (Map) root.get("config");
		if (config == null) {
			config = new HashMap();
		}

		this.mainRules = new ArrayList<TimeRule>(rules.size());

		boolean sortByPatternLength = true;
		if (config.containsKey("sortByPatternLength")) {
			sortByPatternLength = ((Boolean) config.get("sortByPatternLength")).booleanValue();
		}
		if (config.containsKey("min_year")) {
			min_year = (Integer) config.get("min_year");
		}
		if (config.containsKey("max_year")) {
			max_year = (Integer) config.get("max_year");
		}
		if (config.containsKey("early_month")) {
			early_month = (Integer) config.get("early_month");
		}
		if (config.containsKey("late_month")) {
			late_month = (Integer) config.get("late_month");
		}

		mainRules = loadRules(rules, sortByPatternLength);

		if (root.containsKey("transformRules")) {
			transformRules = loadRules((List) root.get("transformRules"), sortByPatternLength);
		}
		
	}

	/**
	 * load the time expression rules from 'in'. These rules should be in YAML
	 * format.
	 * 
	 * @param file
	 *            the file object
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public void load(File file) throws IOException {
		InputStream in = null;
		try {
			in = new FileInputStream(file);
			load(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	public Map getConfig() {
		return config;
	}

	private List<TimeRule> loadRules(List rules, boolean sortByPatternLength) {
		List<TimeRule> result = new ArrayList<TimeRule>();

		for (Object obj : rules) {
			result.addAll(loadRule((Map) obj));
		}

		if (sortByPatternLength) {
			Comparator<TimeRule> cmp = new Comparator<TimeRule>() {
				public int compare(TimeRule o1, TimeRule o2) {
					return o2.getPatternItems().length - o1.getPatternItems().length;
				}
			};
			Collections.sort(result, cmp);
		}

		return result;
	}

	public List<TimeRule> loadRule(Map params) {
		List<TimeRule> rules = new ArrayList<TimeRule>();
		String type = (String) params.get("type");
		Map ruleParams = (Map) params.get("params");

		if (type.equals("simple")) {

			for (PatternItem[] patterns : getPatterns(getPatternStrings(params))) {
				SimpleTimeRule timeRule = new SimpleTimeRule();
				timeRule.setTimeAnnotator(this);
				timeRule.setPatternItems(patterns);
				timeRule.setParameters(ruleParams);
				rules.add(timeRule);
			}
		} else if (type.equals("script")) {
			for (PatternItem[] patterns : getPatterns(getPatternStrings(params))) {
				TimeRule timeRule = new ScriptRule();
				timeRule.setTimeAnnotator(this);
				timeRule.setParameters(ruleParams);
				timeRule.setPatternItems(patterns);
				rules.add(timeRule);
			}
		} else {
			new RuntimeException(type + " is not supported.");
		}

		return rules;
	}

	public List<PatternItem[]> getPatterns(List<String[]> patternStrings) {
		List<PatternItem[]> result = new ArrayList<PatternItem[]>();
		Pattern regexp = Pattern.compile("\\(regex:(.*)\\)");
		Matcher m;

		for (String[] patternString : patternStrings) {
			PatternItem[] patternItem = new PatternItem[patternString.length];
			for (int i = 0; i < patternString.length; i++) {
				if (patternString[i].equals("(number)")) {
					patternItem[i] = new NumberPattern();
				} else if (patternString[i].equals("(ordinal)")) {
					patternItem[i] = new NumberPattern(NumberPattern.Ordinal.MUST);
				} else if (patternString[i].equals("(year)")) {
					patternItem[i] = new NumberPattern(min_year, max_year);
				} else if (patternString[i].equals("(month)")) {
					patternItem[i] = new MonthPattern();
				} else if (patternString[i].equals("(day)")) {
					patternItem[i] = new NumberPattern(1, 31);
				} else if (patternString[i].equals("(dow)")) {
					patternItem[i] = new DayOfWeekPattern();
				} else if (patternString[i].equals("(time)")) {
					patternItem[i] = new TimePattern(false);
				} else if (patternString[i].equals("(duration)")) {
					patternItem[i] = new TimePattern(true);
				} else if ((m = regexp.matcher(patternString[i])).matches()) {
					Pattern p = Pattern.compile(m.group(1));
					patternItem[i] = new RegexPattern(p);
				} else {
					patternItem[i] = new StringPattern(patternString[i]);
				}
			}

			result.add(patternItem);
		}

		return result;
	}

	public List<String[]> getPatternStrings(Map params) {
		List<String> patterns;

		if (params.containsKey("patterns")) {
			patterns = (List<String>) params.get("patterns");
		} else if (params.containsKey("pattern")) {
			patterns = new ArrayList<String>(1);
			patterns.add((String) params.get("pattern"));
		} else {
			throw new RuntimeException();
		}

		List<String[]> result = new ArrayList<String[]>(patterns.size());
		for (String pattern : patterns) {
			result.add(pattern.split("\\s+"));
		}

		return result;
	}

	private int nextOffset(List<Annotation> tokens, int offset, Span span) {
		int i = offset;
		while (i < tokens.size()) {
			if (tokens.get(i).start() >= span.end()) {
				break;
			}
			i++;
		}

		return i;
	}

	public DateTime normalizeMonth(DateTime ref, int month) {
		int refMonth = ref.getMonthOfYear();
		int refYear = ref.getYear();
		DateTime result = ref.withField(DateTimeFieldType.monthOfYear(), month);

		if (refMonth < early_month && month > late_month) {
			result = result.withField(DateTimeFieldType.year(), refYear - 1);
		} else if (refMonth > late_month && month < early_month) {
			result = result.withField(DateTimeFieldType.year(), refYear + 1);
		}

		return result;
	}
}
