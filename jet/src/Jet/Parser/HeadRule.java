// -*- tab-width: 4 -*-
package Jet.Parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Jet.Format.InvalidFormatException;
import Jet.Util.IOUtils;
import Jet.Util.StringUtils;

/**
 * Head rule.
 *
 * @author Akira ODA
 */
public class HeadRule {
	/** path to default rule file */
	private static final String DEFAULT_RULE_PATH = "Jet/Parser/resources/head_rule.txt";

	/** encoding of default rule file */
	private static final String DEFAULT_RULE_ENCODING = "US-ASCII";

	/** singleton instance of default rule */
	private static final HeadRule defaultInstance = createDefaultRule();

	/**
	 * Entry of head rule.
	 *
	 * @author Akira ODA
	 */
	public static class HeadRuleEntry {
		private String category;

		private ScanDirection direction;

		private List<String> priorityList;

		public HeadRuleEntry(String category, ScanDirection direction, List<String> priorityList) {
			this.category = category;
			this.direction = direction;
			this.priorityList = priorityList;
		}

		public HeadRuleEntry() {
			this(null, null, new ArrayList());
		}

		public String getCategory() {
			return category;
		}

		public void setCategory(String category) {
			this.category = category;
		}

		public ScanDirection getDirection() {
			return direction;
		}

		public void setDirection(ScanDirection direction) {
			this.direction = direction;
		}

		public List<String> getPriorityList() {
			return priorityList;
		}

		public void setPriorityList(List<String> priorityList) {
			this.priorityList = priorityList;
		}

		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append(this.category);
			switch (this.direction) {
			case LEFT_TO_RIGHT:
				builder.append(" LR ");
				break;

			case RIGHT_TO_LEFT:
				builder.append(" RL ");
				break;
			}

			builder.append(StringUtils.join(" ", priorityList));
			return builder.toString();
		}
	}

	public enum ScanDirection {
		LEFT_TO_RIGHT, RIGHT_TO_LEFT
	}

	/** List of head rule entry */
	private Map<String, HeadRuleEntry> rules;

	private HeadRule() {
		rules = new HashMap<String, HeadRuleEntry>();
	}

	/**
	 * Creates <code>HeadRule<code> which has default rules.
	 * @return created rule instance
	 */
	public static HeadRule createDefaultRule() {
		HeadRule instance = new HeadRule();
		InputStream rawIn = HeadRule.class.getClassLoader().getResourceAsStream(DEFAULT_RULE_PATH);
		Reader in = null;

		try {
			in = new InputStreamReader(rawIn, DEFAULT_RULE_ENCODING);
			instance.load(in);
		} catch (UnsupportedOperationException ex) {
			throw new RuntimeException(ex);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		} catch (InvalidFormatException ex) {
			throw new RuntimeException(ex);
		} finally {
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(rawIn);
		}

		return instance;
	}

	/**
	 * Returns default rule instance.
	 *
	 * @return
	 */
	public static HeadRule getDefaultRule() {
		return defaultInstance;
	}

	/**
	 * Returns rule instance which is read from <code>java.io.Reader</code> object.
	 *
	 * @param in
	 * @return
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	public static HeadRule getRule(Reader in) throws IOException, InvalidFormatException {
		HeadRule rule = new HeadRule();
		rule.load(in);
		return rule;
	}

	/**
	 * Returns rule instance which is read from file.
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	public static HeadRule getRule(File file) throws IOException, InvalidFormatException {
		HeadRule rule = new HeadRule();
		rule.load(file);
		return rule;
	}

	/**
	 * Returns head element's index in node's children.
	 *
	 * @param node
	 *            target parse tree node
	 * @return head element's index which is zero origin, or -1 if it cannot
	 *         determine head element.
	 */
	public int getHead(ParseTreeNode node) {
		String category = (String) node.category;		
		HeadRuleEntry entry = rules.get(category);
		ParseTreeNode[] children = node.children;
		
		// special rule for NPs (as used by Magerman, improved by Collins)
		// see http://people.csail.mit.edu/mcollins/papers/heads
		// first look for last node with category nn, nns, nnp, nnps, nx, pos, jjr
		if (category.equals("np")) {
			for (int i=children.length-1; i >= 0; i--) {
				String ccat = (String) children[i].category;
				if (ccat.startsWith("nn") || ccat.equals("nx") || 
				    ccat.equals("pos") || ccat.equals("jjr"))
					return i + 1;
			}
			// second, look for first np node
			for (int i=0; i<children.length; i++) {
				String ccat = (String) children[i].category;
				if (ccat.equals("np"))
					return i + 1;
			}
			// Collins has additional special cases, not included here
		}

		if (entry == null) {
			if (children.length == 1) {
				return 1;
			} else {
				return 0;
			}
		}

		int start, end, step;
		switch (entry.getDirection()) {
		case LEFT_TO_RIGHT:
			start = 0;
			end = children.length;
			step = 1;
			break;

		case RIGHT_TO_LEFT:
			start = children.length - 1;
			end = -1;
			step = -1;
			break;

		default:
			// unreachable
			throw new InternalError();
		}

		for (String priority : entry.getPriorityList()) {
			for (int i = start; i != end; i += step) {
				ParseTreeNode child = children[i];
				if (priority.equals(child.category)) {
					return i + 1;
				}
			}
		}

		for (int i = start; i != end; i += step) {
			if (!isTerminal(children[i])) {
				return i + 1;
			} else if (!isPunctuationOrParenthesis(children[i])) {
				return i + 1;
			}
		}

		return start;
	}

	/**
	 * Applys head rule and sets head index each node in the parse tree.
	 *
	 * @param tree
	 */
	public void apply(ParseTreeNode tree) {
		if (tree.children == null || tree.children.length == 0) {
			return;
		}

		tree.head = getHead(tree);
		for (ParseTreeNode child : tree.children) {
			apply(child);
		}
	}

	/**
	 * Adds rule entry
	 *
	 * @param category
	 *            target category
	 * @param direction
	 *            scan direction
	 * @param priorityList
	 *            head priority list
	 */
	public void addEntry(String category, ScanDirection direction, List<String> priorityList) {
		HeadRuleEntry entry = new HeadRuleEntry(category, direction, priorityList);
		rules.put(category, entry);
	}

	/**
	 * Loads head rule from stream.
	 *
	 * @param in
	 * @throws IOException
	 *             if I/O error occurs
	 * @throws InvalidFormatException
	 *             if rule readed from stream is invalid format
	 */
	private void load(Reader in) throws IOException, InvalidFormatException {
		LineNumberReader input = null;
		if (in instanceof BufferedReader) {
			input = new LineNumberReader(in);
		} else {
			input = new LineNumberReader(new BufferedReader(in));
		}

		Pattern ruleRegex = Pattern.compile(
				"^(\\w+) \\s+ (left-to-right|right-to-left) \\s+ (.*)$", Pattern.COMMENTS);
		Pattern separator = Pattern.compile("\\s+");

		String line;
		while ((line = input.readLine()) != null) {
			if (line.startsWith("#")) {
				continue;
			}

			Matcher matcher = ruleRegex.matcher(line);
			if (!matcher.matches()) {
				throw new InvalidFormatException("Invalid format at line " + input.getLineNumber());
			}

			String category = matcher.group(1).toLowerCase();
			ScanDirection direction = getScanDirection(matcher.group(2));
			List<String> priorityList = Arrays.asList(separator.split(matcher.group(3)
					.toLowerCase()));
			this.addEntry(category, direction, priorityList);
		}
	}

	/**
	 * loads head rule from specified file.
	 *
	 * @param file
	 * @throws IOException
	 *             if I/O error occurs
	 * @throws InvalidFormatException
	 *             if rule readed from stream is invalid format
	 */
	private void load(File file) throws IOException, InvalidFormatException {
		Reader reader = null;
		try {
			reader = new FileReader(file);
			load(reader);
		} finally {
			IOUtils.closeQuietly(reader);
		}
	}

	/**
	 * Returns Direction object corresponds key.
	 *
	 * @param key
	 *            direction string in rule file.
	 * @return if key is "left-to-right" or "right-to-left", Direction object
	 *         corresponding key, else null.
	 */
	private ScanDirection getScanDirection(String key) {
		if (key.equals("left-to-right")) {
			return ScanDirection.LEFT_TO_RIGHT;
		} else if (key.equals("right-to-left")) {
			return ScanDirection.RIGHT_TO_LEFT;
		} else {
			return null;
		}
	}

	/**
	 * Returns if node is terminal.
	 *
	 * @param node
	 * @return
	 */
	private boolean isTerminal(ParseTreeNode node) {
		if (node.children != null) {
			return false;
		} else {
			return true;
		}
	}

	private static final Pattern puncOrParenPattern = Pattern.compile("[\\p{P}\\p{Ps}\\p{Pc}]+");

	/**
	 * Returns if terminal node is punctuation or parenthesis.
	 *
	 * @param node
	 * @return
	 */
	private boolean isPunctuationOrParenthesis(ParseTreeNode node) {
		return puncOrParenPattern.matcher(node.word).matches();
	}
}
