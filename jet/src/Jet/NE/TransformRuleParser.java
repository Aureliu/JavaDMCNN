// -*- tab-width: 4 -*-
package Jet.NE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Jet.Util.StringUtils;

public class TransformRuleParser {
	private static final char PREFIX_CHANGE_FORCE = '!';

	private static final char PREFIX_MATCH_NOT = '!';

	private static final char PREFIX_MATCH_SPECIAL = '~';

	private static final Pattern MATCH_RULE_SEPARATOR = Pattern.compile("\\s+");

	private static final Pattern PATTERN_SEPARATOR = Pattern.compile("\\|");

	private static final int OFFSET_STRING_MATCH_RULE = 0;

	private static final int OFFSET_CLASS_MATCH_RULE = 1;

	private static final int OFFSET_POS_MATCH_RULE = 2;

	private static final int OFFSET_NE_MATCH_RULE = 3;
	
	private static final int OFFSET_RE_MATCH_RULE = 4;

	private static final String PREFIX_NE_BIO_B = "B-";

	private static final String PREFIX_NE_BIO_I = "I-";

	private static final String NE_BIO_O = "O";

	/**
	 * Parse list of Named Entity Transform rules.
	 *
	 * @param in
	 * @return
	 * @throws IOException
	 * @throws RuleFormatException
	 */
	public List<TransformRule> parse(Reader in) throws IOException,
			RuleFormatException {
		BufferedReader bin;

		if (in instanceof BufferedReader) {
			bin = (BufferedReader) in;
		} else {
			bin = new BufferedReader(in);
		}

		List<TransformRule> rules = new ArrayList<TransformRule>();
		String line;
		while ((line = bin.readLine()) != null) {
			int pos = line.indexOf('#');
			if (pos >= 0) {
				line = line.substring(0, pos);
			}
			line = line.trim();

			if (line.length() > 0) {
				rules.add(parseLine(line));
			}
		}

		return rules;
	}

	/**
	 * Parse transform rule line.
	 *
	 * @param line
	 * @return
	 * @throws RuleFormatException
	 */
	private TransformRule parseLine(String line) throws RuleFormatException {
		int pos = line.indexOf('>');
		if (pos < 0) {
			throw new RuleFormatException("change rule not.");
		}

		String pattern = line.substring(0, pos).trim();
		String change = line.substring(pos + 1).trim();
		ChangeType changeType = ChangeType.NORMAL;
		if (change.charAt(0) == PREFIX_CHANGE_FORCE) {
			changeType = ChangeType.FORCE;
			change = change.substring(1).trim();
		}

		TransformRule rule = new TransformRule();
		rule.setMatchRules(parseMatchRule(pattern));
		rule.setChangeRule(changeType, parseChangeRule(change));
		return rule;
	}

	/**
	 * Parse matching rules.
	 *
	 * @param pattern
	 * @return
	 * @throws RuleFormatException
	 */
	private MatchRuleItem[] parseMatchRule(String pattern)
			throws RuleFormatException {

		Pattern regex = Pattern.compile("\\s*\\((.*?)\\)\\s*");
		Matcher matcher = regex.matcher(pattern);
		List<String> elements = new ArrayList<String>();

		// extract earch elements.
		while (matcher.regionStart() < pattern.length()) {
			if (!matcher.lookingAt()) {
				throw new RuleFormatException("Ilegral matching rule.");
			}

			elements.add(matcher.group(1));
			matcher.region(matcher.end(), matcher.regionEnd());
		}

		MatchRuleItem[] rule = new MatchRuleItem[elements.size()];
		for (int i = 0, len = elements.size(); i < len; i++) {
			rule[i] = parseMatchRuleItem(elements.get(i));
		}

		return rule;
	}

	/**
	 * Parse transform rule.
	 *
	 * @param change
	 * @return
	 * @throws RuleFormatException
	 */
	private ChangeRule[] parseChangeRule(String change)
			throws RuleFormatException {

		String[] items = change.split("\\s+");
		ChangeRule[] rules = new ChangeRule[items.length];

		for (int i = 0; i < items.length; i++) {
			int pos = items[i].indexOf('=');

			if (pos < 0) {
				throw new RuleFormatException();
			}

			String numStr = items[i].substring(0, pos).trim();
			int changePos;
			try {
				changePos = Integer.parseInt(numStr) - 1;
			} catch (NumberFormatException ex) {
				throw new RuleFormatException('"' + numStr + "\" is not number");
			}

			if (changePos < 0) {
				throw new RuleFormatException('"' + numStr + "\" is invalid number");
			}

			String neStr = items[i].substring(pos + 1).trim();
			NamedEntityAttribute ne = parseNamedEntityExpression(neStr);

			rules[i] = new ChangeRule(changePos, ne);
		}

		return rules;
	}

	private MatchRuleItem parseMatchRuleItem(String itemString)
			throws RuleFormatException {

		String[] items = MATCH_RULE_SEPARATOR.split(itemString);
		MatchRuleItem rule = new MatchRuleItem();

		for (int i = 0; i < items.length; i++) {
			if (items[i].equals("*")) {
				continue;
			} else if (items[i].length() == 0) {
				throw new RuleFormatException();
			}

			String item = items[i];
			MatchType matchType;
			switch (item.charAt(0)) {
			case PREFIX_MATCH_NOT:
				item = item.substring(1);
				matchType = MatchType.NOT;
				break;

			case PREFIX_MATCH_SPECIAL:
				item = item.substring(1);
				matchType = MatchType.SPECIAL;
				break;

			default:
				matchType = MatchType.NORMAL;
			}

			String[] array = PATTERN_SEPARATOR.split(item);
			switch (i) {
			case OFFSET_STRING_MATCH_RULE:
				rule.setStringRule(parseStringRule(matchType, array));
				break;

			case OFFSET_CLASS_MATCH_RULE:
				rule.setClassRule(parseClassRule(matchType, array));
				break;

			case OFFSET_POS_MATCH_RULE:
				rule.setPartOfSpeechRule(parsePartOfSpeechRule(matchType, array));
				break;

			case OFFSET_NE_MATCH_RULE:
				rule.setNamedEntityRule(parseNamedEntityRule(matchType, array));
				break;
				
			case OFFSET_RE_MATCH_RULE:
				rule.setRegexpRule(parseRegexpRule(matchType, array));
				break;

			default:
				throw new RuleFormatException();
			}
		}

		return rule;
	}

	private StringRule parseStringRule(MatchType type, String[] pattern)
			throws RuleFormatException {

		switch (type) {
		case NORMAL:
		case NOT:
		case ANY:
			return new StringRule(type, pattern);

		default:
			throw new RuleFormatException();
		}
	}

	private ClassRule parseClassRule(MatchType type, String[] pattern)
			throws RuleFormatException {
		
		// strip prefix 'N-'
		for (int i = 0; i < pattern.length; i++) {
			if (pattern[i].startsWith("N-")) {
				pattern[i] = pattern[i].substring(2);
			}
		}
		
		switch (type) {
		case NORMAL:
		case NOT:
		case ANY:
			return new ClassRule(type, pattern);

		case SPECIAL:
			if (pattern.length == 1) {
				return new ClassRule(type, pattern);
			} else {
				// TODO: error message.
				throw new RuleFormatException();
			}

		default:
			throw new RuleFormatException();
		}
	}

	private PartOfSpeechRule parsePartOfSpeechRule(MatchType type, String[] pattern)
			throws RuleFormatException {

		switch (type) {
		case NORMAL:
		case NOT:
		case ANY:
			return new PartOfSpeechRule(type, pattern);

		default:
			throw new RuleFormatException();
		}
	}

	private NamedEntityRule parseNamedEntityRule(MatchType type, String[] pattern)
			throws RuleFormatException {

		NamedEntityAttribute[] attrs = new NamedEntityAttribute[pattern.length];

		for (int i = 0; i < pattern.length; i++) {
			attrs[i] = parseNamedEntityExpression(pattern[i]);
		}

		return new NamedEntityRule(type, attrs);
	}
	
	private RegexpRule parseRegexpRule(MatchType type, String[] pattern) throws RuleFormatException {
		return new RegexpRule(type, StringUtils.join("|", pattern));
	}

	private NamedEntityAttribute parseNamedEntityExpression(String str) throws RuleFormatException {
		if (str.equals(NE_BIO_O)) {
			return new NamedEntityAttribute(null, BioType.O);
		} else if (str.startsWith(PREFIX_NE_BIO_B)) {
			String ne = str.substring(PREFIX_NE_BIO_B.length());
			return new NamedEntityAttribute(ne, BioType.B);
		} else if (str.startsWith(PREFIX_NE_BIO_I)) {
			String ne = str.substring(PREFIX_NE_BIO_I.length());
			return new NamedEntityAttribute(ne, BioType.I);
		} else {
			throw new RuleFormatException('"' + str + "\" is not named entity name");
		}
	}
}
