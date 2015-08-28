// -*- tab-width: 4 -*-
package Jet.Chunk;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Jet.Format.InvalidFormatException;
import Jet.Format.PTBReader;
import Jet.Format.Treebank;
import Jet.Lisp.FeatureSet;
import Jet.Parser.ParseTreeNode;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

public class TreeBasedChunker {
	private static final Set<String> PRUNE_ALWAYS;

	private static final Set<List<String>> PRUNE_IF_IN_FRONT_OF_HEAD;

	private static final Map<String, Pattern> HEAD_CAT;

	private static final Map<String, Pattern> HEAD;

	private static final String chunkTagName = "chunk";

	static {
		PRUNE_ALWAYS = new HashSet<String>(asList("NAC", "QP", "NX", "X"));

		PRUNE_IF_IN_FRONT_OF_HEAD = new HashSet<List<String>>();
		PRUNE_IF_IN_FRONT_OF_HEAD.add(asList("NP", "ADJP"));
		PRUNE_IF_IN_FRONT_OF_HEAD.add(asList("NP", "UCP"));
		PRUNE_IF_IN_FRONT_OF_HEAD.add(asList("WHNP", "WHADJP"));
		PRUNE_IF_IN_FRONT_OF_HEAD.add(asList("ADJP", "ADVP"));

		HEAD = new HashMap<String, Pattern>();
		HEAD.put("ADJP", Pattern.compile("JJ|RB|VB|IN|UH|FW|RP|$|#|DT|NN"));
		HEAD.put("ADVP", Pattern.compile("RB|IN|TO|DT|PDT|JJ|RP|FW|LS|UH|CC|NN|CD|VB"));
		HEAD.put("CONJP", Pattern.compile("CC|IN|RB"));
		HEAD.put("INTJ", Pattern.compile("UH|RB|NN|VB|FW|JJ"));
		HEAD.put("LST", Pattern.compile("LS|JJ|:"));
		HEAD.put("NAC", Pattern.compile("NN"));
		HEAD.put("NOLABEL", Pattern.compile("[A-Z]"));
		HEAD.put("NP", Pattern.compile("NN|CD|PRP|JJ|DT|EX|IN|RB|VB|FW|SYM|UH|WP|WDT"));
		HEAD.put("NX", Pattern.compile("NN|CD|PRP|JJ|DT|EX|FW|SYM|UH|WP|WDT"));
		HEAD.put("PP", Pattern.compile("IN|TO|RB|VBG|VBN|JJ|RP|CC|FW"));
		HEAD.put("PRT", Pattern.compile("RP|IN|RB|JJ"));
		HEAD.put("QP", Pattern.compile("CD|DT|NN|JJ"));
		HEAD.put("SBAR", Pattern.compile("IN|WDT"));
		HEAD.put("UCP", Pattern.compile("JJ|NN|VB|CD"));
		HEAD.put("VP", Pattern.compile("VB|MD|TO|JJ|NN|POS|FW|SYM|AUX|AUXG"));
		HEAD.put("WHADJP", Pattern.compile("JJ"));
		HEAD.put("WHADVP", Pattern.compile("WRB|IN|RB|WDT"));
		HEAD.put("WHNP", Pattern.compile("WDT|WP|CD|DT|IN|NN|JJ|RB"));
		HEAD.put("WHPP", Pattern.compile("IN|TO"));

		HEAD_CAT = new HashMap<String, Pattern>();
		HEAD_CAT.put("ADJP", Pattern.compile("^ADJP"));
		HEAD_CAT.put("ADVP", Pattern.compile("^ADVP|.*-ADV"));
		HEAD_CAT.put("CONJP", Pattern.compile("^CONJP"));
		HEAD_CAT.put("FRAG", Pattern.compile("^FRAG|INTJ|S|VP"));
		HEAD_CAT.put("INTJ", Pattern.compile("^S|VP|INTJ"));
		HEAD_CAT.put("LST", Pattern.compile("^LST"));
		HEAD_CAT.put("NOLABEL", Pattern.compile("^[A-Z]"));
		HEAD_CAT.put("NP", Pattern.compile("^NP|NX|.*-NOM"));
		HEAD_CAT.put("NX", Pattern.compile("^NX"));
		HEAD_CAT.put("PP", Pattern.compile("^PP"));
		HEAD_CAT.put("PRN", Pattern.compile("^S|VP"));
		HEAD_CAT.put("PRT", Pattern.compile("^PRT"));
		HEAD_CAT.put("RRC", Pattern.compile("^S|VP"));
		HEAD_CAT.put("S", Pattern.compile("^S$|VP|.*-PRD"));
		HEAD_CAT.put("SBAR", Pattern.compile("^SBAR|S|WH"));
		HEAD_CAT.put("SBARQ", Pattern.compile("^SBARQ|SQ|WH"));
		HEAD_CAT.put("SINV", Pattern.compile("^SINV|VP|SBAR"));
		HEAD_CAT.put("SQ", Pattern.compile("^SQ|VP|S|WH"));
		HEAD_CAT.put("UCP", Pattern.compile("^[A-Z]+P(-[-A-Z]+)?$|S"));
		HEAD_CAT.put("VP", Pattern.compile("^VP"));
		HEAD_CAT.put("WHADJP", Pattern.compile("^WHADJP|ADJP"));
		HEAD_CAT.put("WHADVP", Pattern.compile("^WHADVP"));
		HEAD_CAT.put("WHNP", Pattern.compile("^WHNP|NP"));
		HEAD_CAT.put("WHPP", Pattern.compile("^WHPP"));
		HEAD_CAT.put("X", Pattern.compile("^S|[A-Z]+P(-[-A-Z]+)?$"));
	}

	public static void main(String[] args) throws IOException, InvalidFormatException {
		PTBReader ptbReader = new PTBReader();
		File file = new File("testdata/wsj_0001.mrg");
		Treebank treebank = ptbReader.load(file);
		Document doc = treebank.getDocument();

		TreeBasedChunker chunker = new TreeBasedChunker();
		for (ParseTreeNode tree : treebank.getParseTreeList()) {
			chunker.chunk(doc, tree);
		}

		doc.setSGMLwrapMargin(0);
		System.out.println(doc.writeSGML(chunkTagName));
	}

	public void chunk(Document doc, ParseTreeNode tree) {
		Node node = convert(tree);
		prune(node);
		List<Terminal> flattened = flatten(node);
		chunks(flattened);

		int i = 0;
		while (i < flattened.size()) {
			Terminal terminal = flattened.get(i);
			String chunkTag = terminal.getChunkTag();

			if (chunkTag.startsWith("B-")) {
				String type = chunkTag.substring(2);
				String iTag = "I-" + type;
				int start = terminal.span().start();
				int end = terminal.span().end();

				i++;
				while (i < flattened.size() && flattened.get(i).getChunkTag().equals(iTag)) {
					end = flattened.get(i).span().end();
					i++;
				}

				Span span = new Span(start, end);
				FeatureSet attrs = new FeatureSet("type", type);
				doc.annotate(chunkTagName, span, attrs);
			} else {
				i++;
			}
		}
	}

	private List<Terminal> flatten(Node node) {
		return flatten(node, new FlattenState());
	}

	private List<Terminal> flatten(Node node, FlattenState state) {
		Matcher matcher = Pattern.compile("([A-Z]+)").matcher(node.getFunction());

		if (node.isTerminal()) {
			return Collections.singletonList((Terminal) node);
		} else if (!node.isTerminal() && matcher.lookingAt()) {
			String tag = matcher.group(1);
			NonTerminal parent = (NonTerminal) node;
			List<Node> children = parent.getChildren();

			state.chunkNumber++;

			for (Node child : children) {
				if (child.isTerminal()) {
					Terminal terminal = (Terminal) child;
					terminal.setChunkTag(String.format("I-%s-%d", tag, state.chunkNumber));
				}

				if (child.isHead()) {
					String function = child.getFunction();
					if (function.length() == 0 || function.startsWith(tag)) {
						child.setFunction(tag);
					} else {
						child.setFunction(String.format("%s/%s", function, tag));
					}
				}
			}

			List<Terminal> result = new ArrayList<Terminal>();
			for (Node child : children) {
				result.addAll(flatten(child, state));
			}

			return result;
		} else {
			System.err.println(node.getFunction());
			throw new RuntimeException();
		}
	}

	private void chunks(List<Terminal> flattened) {
		String headType = null;
		int headNumber = -1;
		int oldNumber = -1;

		Pattern chunkTagPattern = Pattern.compile("^ I - ([A-Z]+) - ([0-9]+) $", Pattern.COMMENTS);

		for (int i = flattened.size() - 1; i >= 0; i--) {
			Terminal current = flattened.get(i);
			Terminal prev = null;
			if (i < flattened.size() - 1) {
				prev = flattened.get(i + 1);
			}

			Matcher m = chunkTagPattern.matcher(current.getChunkTag());
			if (m.matches()) {
				String chunkType = m.group(1);
				int number = Integer.parseInt(m.group(2));

				if (chunkType.startsWith("WH")) {
					chunkType = chunkType.substring(2);
				}

				if (current.getFunction().length() > 0) {
					headType = chunkType;
					headNumber = number;
					current.setChunkTag(String.format("I-%s", chunkType));
				} else {
					if (chunkType.equals(headType) && number == headNumber) {
						current.setChunkTag(String.format("I-%s", chunkType));
					} else if (current.getParfOfSpeech().equals("POS")) {
						current.setChunkTag("I-NP");
						if (prev != null && Pattern.matches("^.-NP", prev.getChunkTag())) {
							number = oldNumber;
						} else {
							number = -2;
						}
					} else {
						current.setChunkTag("O");
					}
				}

				if (oldNumber != number) {
					if (chunkType.startsWith("I-")) {
						current.setChunkTag("E-" + chunkType.substring(2));
					}
					if (chunkType.startsWith("E-")) {
						current.setChunkTag("C-" + chunkType.substring(2));
					}
					if (prev != null) {
						String prevChunkType = prev.getChunkTag();
						if (prevChunkType.startsWith("I-")) {
							prev.setChunkTag("B-" + prevChunkType.substring(2));
						}
					}
				}

				oldNumber = number;

			}
		}

		Terminal first = flattened.get(0);
		if (first.getChunkTag().startsWith("I-")) {
			first.setChunkTag("B-" + first.getChunkTag().substring(2));
		}
		if (first.getChunkTag().startsWith("E-")) {
			first.setChunkTag("E-" + first.getChunkTag().substring(2));
		}
	}

	private void prune(Node node) {
		if (node.isTerminal()) {
			return;
		}

		pruneRecursive((NonTerminal) node);
	}

	private void pruneRecursive(NonTerminal node) {
		PruneState state = new PruneState();

		List<Node> children = node.getChildren();

		int i = 0;
		while (i < children.size()) {
			if (children.get(i).isTerminal()) {
				checkTerminal(node, i, state);
			} else {
				if (checkNonTerminal(node, i, state)) {
					i--;
				}
			}

			i++;
		}

		markHead(node, state);
		pruneADVPInVP(node, state);

		for (Node child : children) {
			if (!child.isTerminal()) {
				pruneRecursive((NonTerminal) child);
			}
		}
	}

	private void markHead(NonTerminal parent, PruneState state) {
		if (state.lastNonRef.size() > 0) {
			int index = state.lastNonRef.get(state.lastNonRef.size() - 1);
			parent.getChild(index).setHead(true);
		} else if (state.subFunctions.size() > 0) {
			if (state.cc) {
				for (int i : state.subFunctions) {
					parent.getChild(i).setHead(true);
				}
			} else {
				parent.getChild(state.subFunctions.get(0)).setHead(true);
			}
		}
	}

	private void pruneADVPInVP(NonTerminal parent, PruneState state) {
		String tag = parent.getFunction();

		if (tag.equals("VP") && state.adverbs.size() > 0 && state.lastNonRef.size() > 0) {
			int add = 0;
			int i = 0;
			List<Integer> adverbs = state.adverbs;
			int last = state.lastNonRef.get(state.lastNonRef.size() - 1);

			while (i < adverbs.size() && adverbs.get(i) < last) {
				int index = adverbs.get(i) + add;
				if (!parent.getChild(index).isTerminal()) {
					NonTerminal child = (NonTerminal) parent.getChild(index);
					add += child.getChildren().size() - 1;
					parent.getChildren().remove(index);
					parent.getChildren().addAll(index, child.getChildren());
				}
				i++;
			}
		}
	}

	private void checkTerminal(NonTerminal parent, int index, PruneState state) {
		String parentFunc = parent.getFunction();
		String posTag = ((Terminal) parent.getChild(index)).getParfOfSpeech();

		if (isHeadOf(parentFunc, posTag)) {
			state.lastNonRef.add(index);
		}

		if (index > 0 && posTag.startsWith("CC")) {
			state.cc = true;
		} else if (posTag.startsWith("RB")) {
			state.adverbs.add(index);
		}
	}

	private boolean isHeadOf(String parentFunc, String posTag) {
		Pattern pattern = HEAD.get(parentFunc);
		if (pattern != null) {
			return pattern.matcher(posTag).lookingAt();
		} else {
			return false;
		}
	}

	private boolean checkNonTerminal(NonTerminal parent, int index, PruneState state) {
		String tag = parent.getFunction();
		String childTag = parent.getChild(index).getFunction();

		if (PRUNE_ALWAYS.contains(childTag)) {
			simplePrune(parent, index);
			return true;
		}

		if (PRUNE_IF_IN_FRONT_OF_HEAD.contains(asList(tag, childTag))) {
			if (state.subFunctions.size() == 0) {
				simplePrune(parent, index);
				return true;
			}
		}

		if (pruneSInVPCondition(parent, index, state)) {
			return pruneSInVP(parent, index);
		}

		if (tag.equals("VP") && childTag.equals("VP") && isVerbsOrAdverbsInFront(index, state)) {
			simplePrune(parent, index);
			return true;
		}

		if (tag.equals("VP") && childTag.equals("ADVP")) {
			state.adverbs.add(index);
			return false;
		}

		if (HEAD_CAT.containsKey(tag) && HEAD_CAT.get(tag).matcher(childTag).matches()) {
			if (!npCondition(parent, index, state)) {
				state.subFunctions.add(index);
			}
			return false;
		}

		if (childTag.equals("CONJP")) {
			state.cc = true;
			return false;
		}

		return false;
	}

	private boolean isVerbsOrAdverbsInFront(int index, PruneState state) {
		int n = state.lastNonRef.size() + state.adverbs.size();
		return n > 0 && index == n;
	}

	private void simplePrune(NonTerminal parent, int index) {
		List<Node> children = parent.getChildren();
		NonTerminal child = (NonTerminal) children.get(index);
		children.remove(index);
		children.addAll(index, child.getChildren());
	}

	private boolean pruneSInVP(NonTerminal parent, int index) {
		if (pruneSInVPEmptySubjectCondition(parent, index)) {
			List<Node> children = ((NonTerminal) parent.getChild(index)).getChildren();
			parent.getChildren().remove(index);
			parent.getChildren().addAll(index, children.subList(1, children.size()));
			return true;
		}
		return false;
	}

	private boolean pruneSInVPCondition(NonTerminal parent, int index, PruneState state) {
		String tag = parent.getFunction();
		String childTag = parent.getChild(index).getFunction();
		if (!tag.equals("VP") || !childTag.equals("S")) {
			return false;
		}

		if (parent.getChild(index).isTerminal()) {
			return false;
		}

		if (!isVerbsOrAdverbsInFront(index, state)) {
			return false;
		}

		NonTerminal child = (NonTerminal) parent.getChild(index);
		if (child.getChildren().size() < 2) {
			return false;
		}

		// cannot check function tags.

		return true;
	}

	private boolean pruneSInVPEmptySubjectCondition(NonTerminal parent, int index) {
		NonTerminal node = (NonTerminal) parent.getChild(index);
		List<Node> children = node.getChildren();

		if (children.get(0).isTerminal()) {
			return false;
		}

		NonTerminal firstGrandchild = (NonTerminal) children.get(0);

		if (firstGrandchild.getChildren().size() != 0) {
			return false;
		}

		if (!children.get(1).getFunction().equals("VP")) {
			return false;
		}

		return true;
	}

	private boolean npCondition(NonTerminal parent, int index, PruneState state) {
		String tag = parent.getFunction();
		Node child = parent.getChild(index);

		if (!tag.equals("NP"))
			return false;
		if (index == parent.getChildren().size() - 1)
			return false;
		if (!parent.getChild(index + 1).getFunction().startsWith("NP"))
			return false;
		if (child.isTerminal())
			return false;

		List<Node> grandchildren = ((NonTerminal) child).getChildren();
		if (grandchildren.size() == 0)
			return false;
		Node last = grandchildren.get(grandchildren.size() - 1);
		if (!last.isTerminal()) {
			return false;
		}

		Terminal terminal = (Terminal) last;
		if (terminal.getParfOfSpeech() != null || !terminal.getParfOfSpeech().equals("POS")) {
			return false;
		}

		return true;
	}

	private static class PruneState {
		List<Integer> lastNonRef = new ArrayList<Integer>();

		List<Integer> subFunctions = new ArrayList<Integer>();

		List<Integer> adverbs = new ArrayList<Integer>();

		boolean cc = false;
	}

	private static class FlattenState {
		int chunkNumber;
	}

	private static abstract class Node {
		private boolean isHead;
		private Annotation ann;

		private String function;

		public Node(String function, Annotation ann) {
			if (function != null) {
				this.function = function.toUpperCase();
			}

			this.ann = ann;
		}

		public String getFunction() {
			return function;
		}

		public void setFunction(String function) {
			this.function = function;
		}

		public boolean isHead() {
			return isHead;
		}

		public void setHead(boolean isHead) {
			this.isHead = isHead;
		}

		public Annotation getAnnotation() {
			return ann;
		}

		public abstract boolean isTerminal();
	}

	private static class Terminal extends Node {
		private String word;

		private String pos;

		private String chunkTag;

		public Terminal(String pos, String word, Annotation ann) {
			super("", ann);
			this.word = word;

			if (pos != null) {
				this.pos = pos.toUpperCase();
			}
		}

		public boolean isTerminal() {
			return true;
		}

		public String getWord() {
			return word;
		}

		public String getParfOfSpeech() {
			return pos;
		}

		public void setPartOfSpeech(String pos) {
			this.pos = pos;
		}

		public void setChunkTag(String chunkTag) {
			this.chunkTag = chunkTag;
		}

		public String getChunkTag() {
			return chunkTag;
		}

		public Span span() {
			return getAnnotation().span();
		}

		public String toString() {
			if (isHead()) {
				return String.format("(%s-H %s)", getParfOfSpeech(), getWord());
			} else {
				return String.format("(%s %s)", getParfOfSpeech(), getWord());
			}
		}
	}

	private static class NonTerminal extends Node {
		private List<Node> children;

		public NonTerminal(String phraseType, List<Node> children, Annotation ann) {
			super(phraseType, ann);
			this.children = children;
		}

		public List<Node> getChildren() {
			return children;
		}

		public Node getChild(int index) {
			return children.get(index);
		}

		public boolean isTerminal() {
			return false;
		}

		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("(");
			builder.append(getFunction());
			builder.append(" ");
			for (Node child : getChildren()) {
				builder.append(child.toString());
				builder.append(" ");
			}
			builder.append(")");
			return builder.toString();
		}
	}

	/**
	 * Converts from <code>ParseTreeNode</code> object to internal node
	 * classes.
	 *
	 * @param source
	 * @return
	 */
	private static Node convert(ParseTreeNode source) {
		if (source.children == null) {
			return new Terminal((String) source.category, source.word, source.ann);
		} else {
			List<Node> children = new ArrayList<Node>(source.children.length);
			for (ParseTreeNode child : source.children) {
				children.add(convert(child));
			}

			return new NonTerminal((String) source.category, children, source.ann);
		}
	}
}
