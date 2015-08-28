// -*- tab-width: 4 -*-
package Jet.Format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Vector;

import Jet.Lisp.FeatureSet;
import Jet.Parser.ParseTreeNode;
import Jet.Tipster.*;
import Jet.Util.IOUtils;
import Jet.Zoner.SentenceSplitter;
import Jet.Zoner.SpecialZoner;
import Jet.Parser.StatParser;
import Jet.Parser.HeadRule;

/**
 * A reader for the output of a Penn Treebank Parser. The methods read a 
 * Penn Treebank corpus and either annotate an existing Document
 * (addAnnotations methods) with <B>constit</B> annotations representing
 * the trees or build a new Jet.Tipster.Document from the parse trees.
 * 
 * @author Akira ODA
 */
public class PTBReader {
	static Pattern tagNamePattern = Pattern.compile(
			"([^-=]+) (?: - ([\\-a-zA-Z]+)*)? (?: [-=] ([\\-\\d]+))?", Pattern.COMMENTS);

	static Pattern specialTagNamePattern = Pattern.compile("-.*-");

	private static final Map<String, String> TRANSFORM_TABLE;

	private static final Set<String> PUNCTUATIONS;

	private static final Set<String> NO_FOLLOWING_SPACE;

	private static final Set<String> DELETE_PREVIOUS_SPACE;

	/**
	 * If true, backslashes are treated as escape character.
	 */
	private boolean backslashAsEscapeChar = true;

	/**
	 * If true, add tokens when read corpus.
	 */
	private boolean isAddingTokens = false;
	
	HeadRule hr = null;

	static {
		TRANSFORM_TABLE = new HashMap<String, String>();
		TRANSFORM_TABLE.put("-LRB-", "(");
		TRANSFORM_TABLE.put("-LCB-", "{");
		TRANSFORM_TABLE.put("-LSB-", "[");

		TRANSFORM_TABLE.put("-RRB-", ")");
		TRANSFORM_TABLE.put("-RCB-", "}");
		TRANSFORM_TABLE.put("-RSB-", "]");

		PUNCTUATIONS = new HashSet<String>();
		PUNCTUATIONS.add(".");
		PUNCTUATIONS.add(",");
		PUNCTUATIONS.add("?");
		PUNCTUATIONS.add("!");

		NO_FOLLOWING_SPACE = new HashSet<String>();
		NO_FOLLOWING_SPACE.add("(");
		NO_FOLLOWING_SPACE.add("{");
		NO_FOLLOWING_SPACE.add("[");

		DELETE_PREVIOUS_SPACE = new HashSet<String>();
		DELETE_PREVIOUS_SPACE.add(")");
		DELETE_PREVIOUS_SPACE.add("}");
		DELETE_PREVIOUS_SPACE.add("]");
		DELETE_PREVIOUS_SPACE.add(".");
		DELETE_PREVIOUS_SPACE.add(",");
	}
	
	/**
	 *  a list of strings which are deleted in preparing text for the Charniak
	 *  parser, and so should be skipped when matching the text and parser output.
	 */
	 
	private static final String[] skip = 
	  new String[] {"....", "...", "uh,", "Uh,", "um,", "Um,", 
	                "&lt;", "&LT;", "&gt;", "&GT;", "_"};

	/**
	 *  when matching an existing document text against a parse tree,
	 *  each pair of elements represents an allowable match (due to 
	 *  Adam's text-regularization script)
	 */
	 
	private static final String[] match =
	  new String[] {"\"", "``",
	                "\"", "''",
		              "&quot;", "``",
	                "&quot;", "''",
		              "&QUOT;", "``",
	                "&QUOT;", "''",   
	                "&amp;", "&",
	                "&AMP;", "&",
	                "wo", "will",
	                "Wo", "Will",
	                "((", "(",
	                "))", ")"};

	/**
	 * Adds <B>constit</B> annotations to an existing Document <CODE>doc</CODE> to
	 * represent the parse tree structure <CODE>tree</CODE>.
	 * 
	 * @param tree          the parse tree (for a portion of Document doc)
	 * @param doc           the document
	 * @param span          the portion of doc covered by the parse tree
	 * @param jetCategories if true, use Jet categories as terminal categories
	 *                      (if false, use categories read from parse trees)
	 */
	 
	public void addAnnotations(ParseTreeNode tree, Document doc, Span span,
	                           boolean jetCategories) {
		List<ParseTreeNode> terminalNodes = getTerminalNodes(tree);
		String text = doc.text();
		int offset = span.start();

		for (ParseTreeNode terminal : terminalNodes) {
			while (offset < span.end() && Character.isWhitespace(text.charAt(offset))) {
				offset++;
			}
			for (String skipString : skip) {
				if (text.startsWith(skipString, offset)) {
					offset += skipString.length();
					while (offset < span.end() && Character.isWhitespace(text.charAt(offset))) {
						offset++;
					}
					break;
				}
			}
			// match next terminal node against next word in text
			int matchLength = matchTextToTree (text, offset, terminal.word);
			if (matchLength > 0) {
				int endOffset = offset + matchLength;
				while (endOffset < span.end() && Character.isWhitespace(text.charAt(endOffset))) {
					endOffset++;
				}
				terminal.start = offset;
				terminal.end = endOffset;
				offset = endOffset;
			} else {
				System.err.println ("PTBReader.addAnnotations:  " +
				                    "Cannot determine parse tree offset for word " +
				                    terminal.word);
				System.err.println ("  at document offset " + offset + " in sentence");
				System.err.println ("  " + doc.text(span));
				return;
			}
		}

		if (jetCategories) {
			setJetAnnotations (tree, span, doc);
			StatParser.deleteUnusedConstits (doc, span, tree.ann); //<<<
		} else {
			determineNonTerminalSpans(tree, span.start());
			setAnnotations (tree, doc);
		}
	}
	
	/**
	 *  determines whether string <CODE>text</CODE>, beginning at 
	 *  <CODE>offset</CODE>, matches the string <CODE>word</CODE> in a
	 *  PennTreeBank tree.  This may be an exact match, or may
	 *  reflect some regularization of the word for the PTB parser.
	 *
	 *  @return  if a successful match, the number of characters in text
	 *           which were matched;  else -1
	 */
	
	private static int matchTextToTree (String text, int offset, String word) {
		if (word.equals("can") && text.startsWith("can't", offset))
			return 2;
		if (word.equals("Can") && text.startsWith("Can't", offset))
			return 2;
		for (int i=0; i < match.length; i+=2) {
			String textPattern = match[i];
			String treePattern = match[i+1];
			if (text.startsWith(textPattern, offset) && word.equals(treePattern))
				return textPattern.length();
		}
		if (text.startsWith(word, offset))
			return word.length();
		// because Adam sometimes deletes '.'s for Charniak
		if (text.startsWith("." + word, offset))
			return word.length() + 1;
		return -1;
	}			

	/**
 	 * Adds <B>constit</B> annotations to an existing Document <CODE>doc</CODE> to
	 * represent the parse tree structure of a set of trees <CODE>trees</CODE>.
	 * 
	 * @param trees
	 *            list of parse trees
	 * @param doc
	 *            document to which annotations should be added
	 * @param targetAnnotation
	 *            name of annotation to determine spans to add parse tree
	 *            annotations.
	 * @param span
	 *            target span.
	 * @param jetCategories
	 *            if false, use lexical categories from Penn Tree Bank;  if
	 *            true, use categories from Jet
	 */
	 
	public void addAnnotations(List<ParseTreeNode> trees, Document doc, String targetAnnotation,
			Span span, boolean jetCategories) {
		List<Annotation> targetList = (List<Annotation>) doc.annotationsOfType(targetAnnotation,
				span);
		Comparator<Annotation> cmp = new Comparator<Annotation>() {
			public int compare(Annotation a, Annotation b) {
				return a.span().compareTo(b.span());
			}
		};

		Collections.sort(targetList, cmp);
		if (trees.size() != targetList.size()) {
			System.err.println ("PTBReader.addAnnotations:  mismatch between number of " +
			                    targetAnnotation + " (" + targetList.size() +
			                    ") and number of trees (" + trees.size() + ")");
		}
		int n = Math.min(trees.size(), targetList.size());
		for (int i = 0; i < n; i++) {
			ParseTreeNode tree = trees.get(i);
			addAnnotations(tree, doc, targetList.get(i).span(), jetCategories);
			targetList.get(i).put("parse", tree.ann);
		}
	}

	/**
 	 * Adds <B>constit</B> annotations to an existing Document <CODE>doc</CODE> to
	 * represent the parse tree structure of a set of trees <CODE>trees</CODE>.
	 * This version is provided for parse tree files which include sentence
	 * offsets.
	 * 
	 * @param trees
	 *            list of parse trees
	 * @param offsets
	 *            list of the starting position (in doc) of the text
	 *            corresponding to each parse tree
	 * @param doc
	 *            document to which annotations should be added
	 * @param targetAnnotation
	 *            name of annotation to get 'parse' feature pointing
	 *            to parse tree
	 * @param span
	 *            target span.
	 * @param jetCategories
	 *            if false, use lexical categories from Penn Tree Bank;  if
	 *            true, use categories from Jet
	 */
	
	public void addAnnotations (List<ParseTreeNode> trees, List<Integer> offsets,
		  Document doc, String targetAnnotation, Span span, boolean jetCategories) {
		if (trees.size() != offsets.size()) {
			System.err.println ("PTBReader.addAnnotations:  mismatch between number of " +
			                    "trees (" + trees.size() + ") and number of offsets (" + 
			                    offsets.size() + ")");
			return;
		}
		for (int i = 0; i < trees.size(); i++) {
			ParseTreeNode tree = trees.get(i);
			int start = offsets.get(i);
			if (start < 0) {
				System.err.println ("PTBReader.addAnnotations:  offset missing for " +
				                    " parse tree " + i);
				continue;
			}
			int end = (i+1 == offsets.size()) ? span.end() : offsets.get(i+1);
			Span sentenceSpan = new Span(start, end);
			addAnnotations(tree, doc, sentenceSpan, jetCategories);
			Vector<Annotation> anns = doc.annotationsAt (start, targetAnnotation);
			if (anns != null && anns.size() > 0) {
				Annotation ann = anns.get(0);
				ann.put("parse", tree.ann);
			}
		}
	}
	
	List<Integer> offsets;

	/**
	 * Loads parse tree corpus from Penn Treebank corpus.
	 * <P>
	 * This method loads the parse trees, but not determine annotation span and not
	 * set annotation.
	 * <P>
	 * Also sets <CODE>offsets</CODE> to a list of the sentence offsets,
	 * if they are encoded as comments preceding each tree.
	 * 
	 * @param in  the Reader from which the Penn Trees are read
	 * @return a List of parse trees
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	 
	public List<ParseTreeNode> loadParseTrees(Reader in) throws IOException, InvalidFormatException {
		List<ParseTreeNode> list = new ArrayList<ParseTreeNode>();
		offsets = new ArrayList<Integer>();
		PushbackReader input = new PushbackReader(in);

		while (true) {
			skipWhitespaceAndComment(input);
			if (lookAhead(input) == -1) {
				break;
			}
			offsets.add(offset);

			ParseTreeNode node = readNode(input);
			list.add(node);
		}

		return list;
	}
	
	public List<ParseTreeNode> loadParseTrees(File file) throws IOException, InvalidFormatException {
		Reader in = null;
		try {
			in = new BufferedReader(new FileReader(file));
			return loadParseTrees(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}
	
	public List<Integer> getOffsets () {
		return offsets;
	}

	/**
	 * Builds Jet.Tipster.Document object from Penn treebank corpus.
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	public Treebank load(Reader in) throws IOException, InvalidFormatException {

		List<ParseTreeNode> trees = new ArrayList<ParseTreeNode>();
		PushbackReader input = new PushbackReader(in);

		int start = 0;
		while (true) {
			skipWhitespace(input);
			if (lookAhead(input) == -1) {
				break;
			}

			ParseTreeNode tree = readNode(input);
			trees.add(tree);
			determineSpans(tree, start);
			setAnnotations(tree, null);
			start = tree.end;
		}

		String text = buildDocumentString(trees);
		Document doc = new Document(text);
		for (ParseTreeNode tree : trees) {
			doc.annotate("sentence", new Span(tree.start, tree.end), new FeatureSet());
			annotate(doc, tree);
		}

		return new Treebank(doc, trees);
	}

	/**
	 * Builds Document object from Penn treebank corpus.
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	public Treebank load(File file) throws IOException, InvalidFormatException {
		Reader in = null;
		try {
			in = new BufferedReader(new FileReader(file));
			return load(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	/**
	 * Builds Document object from Penn treebank corpus.
	 * 
	 * @param file
	 * @param encoding
	 * @return
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	public Treebank load(File file, String encoding) throws IOException, InvalidFormatException {
		InputStream fin = null;
		Reader in = null;

		try {
			fin = new FileInputStream(file);
			in = new InputStreamReader(fin, encoding);
			in = new BufferedReader(in);

			return load(in);
		} finally {
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(fin);
		}
	}

	/**
	 * Sets a backslash is treated as escape character or not.
	 * 
	 * @param b
	 */
	public void setBackslashAsEscapeCharacter(boolean b) {
		this.backslashAsEscapeChar = b;
	}

	/**
	 * Sets a adding tokens automatically or not.
	 * 
	 * @param b
	 */
	public void setAddingToken(boolean b) {
		this.isAddingTokens = b;
	}

	/**
	 * Returns if node is null element.
	 */
	private static boolean isNullNode(ParseTreeNode node) {
		return node.category.equals("-none-");
	}

	/**
	 * Remove last whitespace character and modify annotation span.
	 * 
	 * @param annotations
	 * @param buffer
	 */
	private void modifyAnnotationEnd(List<Annotation> annotations, StringBuilder buffer) {
		ListIterator<Annotation> it = annotations.listIterator(annotations.size());

		if (buffer.length() == 0) {
			return;
		}

		if (!Character.isWhitespace(buffer.charAt(buffer.length() - 1))) {
			return;
		}

		while (it.hasPrevious()) {
			Annotation a = it.previous();
			if (a.end() != buffer.length()) {
				break;
			}

			Span span = new Span(a.start(), a.end() - 1);
			Annotation replacement = new Annotation(a.type(), span, a.attributes());
			it.set(replacement);
		}

		buffer.deleteCharAt(buffer.length() - 1);
	}

	/**
	 * Reads one node from a stream.
	 * 
	 * @param in
	 * @return readed node
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	private ParseTreeNode readNode(PushbackReader in) throws IOException, InvalidFormatException {
		int c = in.read();

		if (c != '(') {
			throw new InvalidFormatException();
		}

		if ((c = lookAhead(in)) == -1) {
			throw new InvalidFormatException();
		}

		if (Character.isWhitespace(c) || c == '(') {
			skipWhitespace(in);
			ParseTreeNode node = readNode(in);
			skipWhitespace(in);
			c = (char) in.read();
			if (c != ')') {
				throw new InvalidFormatException();
			}
			return node;
		}

		String tag = readTagName(in);
		String function = null;
		Matcher m = tagNamePattern.matcher(tag);
		if (m.matches()) {
			tag = m.group(1);
			function = m.group(2);
		} else if (!specialTagNamePattern.matcher(tag).matches()) {
			throw new InvalidFormatException(tag + " is invalid format.");
		}

		if (skipWhitespace(in) == 0) {
			return null;
		}

		ParseTreeNode node;

		if (lookAhead(in) == '(') {
			// has any child node (not terminal node)
			List<ParseTreeNode> children = new ArrayList<ParseTreeNode>();
			do {
				ParseTreeNode child = readNode(in);
				if (!isNullNode(child)) {
					children.add(child);
				}
				skipWhitespace(in);
			} while (lookAhead(in) != ')');

			node = new ParseTreeNode(tag, children.toArray(new ParseTreeNode[0]), 0, 0, 0, function);
		} else {
			// terminal node
			String word = readWord(in);
			node = new ParseTreeNode(tag, null, 0, 0, null, word, function);
		}

		skipWhitespace(in);
		if (in.read() != ')') {
			throw new InvalidFormatException();
		}

		return node;
	}

	/**
	 * skip whitespace characters
	 * 
	 * @param in
	 * @return count of skipped characters.
	 * @throws IOException
	 */
	private int skipWhitespace(PushbackReader in) throws IOException {
		int count = 0;
		int c;
		do {
			c = in.read();
			count++;
		} while (Character.isWhitespace(c) && c != -1);

		if (c != -1) {
			in.unread(c);
		}

		return count - 1;
	}
	
	private StringBuffer comment = new StringBuffer();
	private int offset = -1;

	/**
	 *  skip whitespace characters and comments (characters following a "#"
	 *  on a line).  Also, if a skipped comment consists of a single integer,
	 *  sets <CODE>offset</CODE> to that integer.
	 * 
	 *  @param  in
	 *  @return count of skipped characters.
	 *  @throws IOException
	 */
	private int skipWhitespaceAndComment(PushbackReader in) throws IOException {
		int count = 0;
		boolean inComment = false;
		offset = -1;
		int c;
		do {
			c = in.read();
			count++;
			if (c == '#' && !inComment) {
				inComment = true;
				comment.setLength(0);
			} else if (c == '\n' && inComment) {
				try {
					offset = Integer.parseInt(comment.toString().trim());
				} catch (NumberFormatException e) {
				}
				inComment = false;
			} else if (inComment) {
				comment.append((char) c);
			}
		} while ((Character.isWhitespace(c) || inComment) && c != -1);

		if (c != -1) {
			in.unread(c);
		}

		return count - 1;
	}
	
	/**
	 * Reads a tag name which is after opened parenthesis.
	 * 
	 * @param in
	 * @return readed token string
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	private String readTagName(PushbackReader in) throws IOException, InvalidFormatException {
		StringBuilder buffer = new StringBuilder();
		int c;

		while (true) {
			c = in.read();
			if (c == -1) {
				throw new InvalidFormatException();
			} else if (Character.isWhitespace(c)) {
				break;
			}

			buffer.append((char) c);
		}

		in.unread(c);

		if (buffer.length() == 0) {
			throw new InvalidFormatException();
		}

		return buffer.toString().toLowerCase().intern();
	}

	/**
	 * Reads annotated token.
	 * 
	 * @param in
	 * @return readed token.
	 * @throws IOException
	 * @throws InvalidFormatException
	 */
	private String readWord(PushbackReader in) throws IOException, InvalidFormatException {
		int c;
		StringBuilder buffer = new StringBuilder();
		while (true) {
			c = in.read();

			if (c != -1 && backslashAsEscapeChar && c == '\\') {
				c = in.read();
			}

			if (c == ')') {
				break;
			} else if (c == -1) {
				throw new InvalidFormatException();
			}

			buffer.append((char) c);
		}

		in.unread(c);

		String word = buffer.toString();
		if (TRANSFORM_TABLE.containsKey(word)) {
			word = TRANSFORM_TABLE.get(word);
		}
		return word;
	}

	/**
	 * Look ahead next character.
	 * 
	 * @param in
	 * @return readed character
	 * @throws IOException
	 */
	private int lookAhead(PushbackReader in) throws IOException {
		int c = in.read();
		if (c != -1) {
			in.unread(c);
		}
		return c;
	}

	/**
	 *  converts a set of Penn TreeBank files into text documents.
	 *  Invoked by:  PTBReader inputDir outputDir.  Converts all files with
	 *  extension .mrg in inputDir to text documents, and writes them into
	 *  outputDir.
	 */
	 
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("usage: java " + PTBReader.class.getName() + " ");
			System.exit(1);
		}

		File inputDir = new File(args[0]);
		File outputDir = new File(args[1]);
		PTBReader parser = new PTBReader();
		for (File file : getFiles(new File(args[0]), ".mrg")) {
			String outFilename = removeSuffix(getRelativePath(inputDir, file));
			File outFile = new File(outputDir, outFilename);
			outFile.getParentFile().mkdirs();

			Writer out = new FileWriter(outFile);
			Document doc = parser.load(file).getDocument();
			out.write(doc.text());
			out.close();
		}
	}
	
/* -- alternative main methods for debugging

	static final String home = "../";
	
	public static void main(String[] args) throws Exception {
		String sgmFileName = home + "Ace 05/V4/bc/CNN_CF_20030303.1900.00.sgm";
		String PTBFileName = "PTB.txt";
		ExternalDocument doc = new ExternalDocument("sgml", sgmFileName);
		doc.setAllTags (true);
		doc.open();
		// mark sentences
		List<Annotation> textSegments = (List<Annotation>) doc.annotationsOfType ("TEXT");
		if (textSegments == null) {
			System.out.println ("No <TEXT> in " + doc.fileName() + ", skipped.");
		}
		Annotation ann = textSegments.get(0);
		Span textSpan = ann.span ();
		SentenceSplitter.split (doc, textSpan);
		File f = new File(PTBFileName);
		PTBReader reader = new PTBReader();
		List<ParseTreeNode> trees = reader.loadParseTrees (f);
		reader.addAnnotations (trees, doc, "sentence",  new Span(0, doc.text().length()), false);
		new View (doc, 1);
	}

	public static void main(String[] args) throws Exception {
		String sgmFileName = "article.sgm";
		String PTBFileName = "article.chout";
		Jet.Lex.EnglishLex.readLexicon("data/Jet4.dict");
		ExternalDocument doc = new ExternalDocument("sgml", sgmFileName);
		doc.setAllTags (true);
		doc.open();
		// mark sentences
		SpecialZoner.findSpecialZones (doc);
		List<Annotation> textSegments = (List<Annotation>) doc.annotationsOfType ("TEXT");
		if (textSegments == null) {
			System.out.println ("No <TEXT> in " + doc.fileName() + ", skipped.");
		}
		Annotation ann = textSegments.get(0);
		Span textSpan = ann.span ();
		SentenceSplitter.split (doc, textSpan);
		Vector<Annotation> sentences = doc.annotationsOfType("sentence");
		if (sentences != null) {
			for (Annotation sentence : sentences) {
				Jet.Lex.Tokenizer.tokenize(doc, sentence.span());
				Jet.Lex.Lexicon.annotateWithDefinitions(doc, sentence.span().start(), sentence.span().end());
			}
		}
		File f = new File(PTBFileName);
		PTBReader reader = new PTBReader();
		List<ParseTreeNode> trees = reader.loadParseTrees (f);
		reader.addAnnotations (trees, doc, "sentence",  new Span(0, doc.text().length()), true);
		new View (doc, 1);
	}
*/
	
	private static List<File> getFiles(File dir, String suffix) throws IOException {
		List<File> list = new ArrayList<File>();

		for (File file : dir.listFiles()) {
			if (file.isFile() && file.getName().endsWith(suffix)) {
				list.add(file);
			} else if (file.isDirectory()) {
				list.addAll(getFiles(file, suffix));
			}
		}

		return list;
	}

	private static String getRelativePath(File base, File file) {
		return file.getAbsolutePath().substring(base.getAbsolutePath().length());
	}

	private static String removeSuffix(String filename) {
		int index = filename.lastIndexOf('.');
		if (index >= 0) {
			return filename.substring(0, index);
		} else {
			return filename;
		}
	}

	private String buildDocumentString(List<ParseTreeNode> trees) {
		StringBuilder buffer = new StringBuilder();

		for (ParseTreeNode tree : trees) {
			List<ParseTreeNode> terminals = getTerminalNodes(tree);
			for (ParseTreeNode terminal : terminals) {
				if (terminal.word != null) {
					buffer.append(terminal.word);
					while (buffer.length() < terminal.end) {
						buffer.append(' ');
					}
				}
			}

			// set last character to newline
			if (buffer.charAt(buffer.length() - 1) == ' ') {
				buffer.setCharAt(buffer.length() - 1, '\n');
			}
		}

		return buffer.toString();
	}

	private void determineSpans(ParseTreeNode tree, int offset) {
		List<ParseTreeNode> terminals = getTerminalNodes(tree);
		determineTerminalSpans(terminals, offset);
		determineNonTerminalSpans(tree, offset);
	}

	private void determineTerminalSpans(List<ParseTreeNode> terminals, int offset) {
		int start = offset;
		int n = terminals.size();

		for (int i = 0; i < n; i++) {
			ParseTreeNode current = terminals.get(i);
			ParseTreeNode prev = i > 0 ? terminals.get(i - 1) : null;

			String word = current.word;
			int end = start + (word != null ? word.length() + 1 : 0);
			if (!hasAfterSpace(word)) {
				end--;
			}
			if (hasBeforeSpace(word) && prev != null) {
				if (hasAfterSpace(prev.word)) {
					prev.end--;
					start--;
					end--;
				}
			}

			current.start = start;
			current.end = end;
			start = end;
		}
	}

	private int determineNonTerminalSpans(ParseTreeNode tree, int offset) {
		if (isTerminalNode(tree)) {
			return tree.end;
		} else {

			ParseTreeNode[] children = tree.children;
			if (children.length > 0) {
				for (ParseTreeNode child : children) {
					offset = determineNonTerminalSpans(child, offset);
				}

				tree.start = children[0].start;
				tree.end = children[children.length - 1].end;
			} else {
				tree.start = offset;
				tree.end = offset;
			}

			return tree.end;
		}
	}

	private boolean hasAfterSpace(String word) {
		if (NO_FOLLOWING_SPACE.contains(word)) {
			return false;
		} else {
			return true;
		}
	}

	private boolean hasBeforeSpace(String word) {
		if (DELETE_PREVIOUS_SPACE.contains(word)) {
			return true;
		} else if (isPartOfShortenedForm(word)) {
			return true;
		} else {
			return false;
		}
	}

	private boolean isPartOfShortenedForm(String word) {
		if (word != null) {
			return word.startsWith("'") || word.equals("n't");
		} else {
			return false;
		}
	}

	private void annotate(Document doc, ParseTreeNode node) {
		doc.addAnnotation(node.ann);
		if (node.children != null) {
			Annotation[] children = new Annotation[node.children.length];
			for (int i = 0; i < node.children.length; i++) {
				children[i] = node.children[i].ann;
			}
			node.ann.put("children", children);
			
			for (ParseTreeNode child : node.children) {
				annotate(doc, child);
			}
		}
		
		if (node.children == null && isAddingTokens) {
			// TODO: adds `case' property
			doc.annotate("token", node.ann.span(), new FeatureSet());
		}
	}

	/**
	 * Returns termninal node list in the parse tree.
	 * 
	 * @param tree
	 * @return
	 */
	private List<ParseTreeNode> getTerminalNodes(ParseTreeNode tree) {
		if (tree.children == null || tree.children.length == 0) {
			// terminal node
			if (tree.word != null) {
				return Collections.singletonList(tree);
			}
			return Collections.emptyList();
		} else {
			List<ParseTreeNode> list = new ArrayList<ParseTreeNode>();
			// non terminal node
			for (ParseTreeNode child : tree.children) {
				list.addAll(getTerminalNodes(child));
			}
			return list;
		}
	}

	/**
	 * Returns if node is terminal node.
	 * 
	 * @param node
	 * @return
	 */
	private boolean isTerminalNode(ParseTreeNode node) {
		return node.children == null;
	}

	/**
	 * Creates annotations for each node in parse tree <CODE>node</NODE>.
	 * These annotations are added to the parse tree;  in addition, if
	 * Document <CODE>doc</CODE> is non-empty, they are added to the document.
	 * <P>
	 * Note that this method does not set the "children" attribute.
	 * 
	 * @param node
	 * @param doc
	 */
	private void setAnnotations(ParseTreeNode node, Document doc) {
		Span span = new Span(node.start, node.end);
		FeatureSet attrs = new FeatureSet();
		attrs.put("cat", node.category);
		if (node.head != 0) {
			attrs.put("head", node.head);
		}
		if (node.function != null) {
			attrs.put("func", node.function);
		}

		node.ann = new Annotation("constit", span, attrs);
		if (doc != null) {
			doc.addAnnotation(node.ann);
		}

		if (node.children != null) {
			for (ParseTreeNode child : node.children) {
				setAnnotations(child, doc);
			}
		}
	}
	
	/**
	 * Creates annotations for each node in parse tree <CODE>node</NODE>.
	 * These annotations are added to the parse tree and to the document
	 * <CODE>doc</CODE>.  In constrast to <CODE>setAnnotations</CODE>,
	 * the categories used for terminal nodes are Jet categories obtained by
	 * Jet tokenization and lexical look-up.  This means that hyphenated
	 * items are split, and multi-word names are reduced to a single node.
	 * 
	 * @param node      the root of the parse tree
	 * @param treeSpan  the span of the document matching the parse tree
	 * @param doc       the document to which annotations will be added
	 */

	private void setJetAnnotations(ParseTreeNode node, Span treeSpan, Document doc) {
		StatParser.buildParserInput (doc, treeSpan.start(), treeSpan.end(), false);
		StatParser.fixHyphenatedItems (doc);
		int nameConstitEnd = -1;
		List<ParseTreeNode> terminals = getTerminalNodes(node);
		for (ParseTreeNode terminal : terminals) {
			int terminalEnd = terminal.end;
			// is there a 'name' constituent or 'hyphword' constituent here?
			Vector<Annotation> constits = doc.annotationsAt(terminal.start, "constit");
			Annotation constit = null;
			Annotation nameConstit = null;
			Annotation hyphword = null;
			if (constits != null) {
				for (Annotation c : constits) {
					if (c.get("cat") == "name") {
						nameConstit = c;
					} else if (c.get("cat") == "hyphword") {
						hyphword = c;
					}
					if (constit == null)
						constit = c;
				}
			}
			if (hyphword != null) {
				nameConstit = null;
				constit = hyphword;
			}
			// if there is a name which is not part of a hyphword, associate the
			// name with this (first) terminal node, and mark any remaining terminal
			// nodes which match tokens in the name as empty
			if (nameConstit != null) {
				terminal.end = nameConstit.end();
				terminal.ann = nameConstit;
				nameConstitEnd = nameConstit.end();
			} else if (nameConstitEnd >= 0) {
				terminal.word = null;
			} else {
				Span span = new Span(terminal.start, terminal.end);
				String pennPOS = ((String) terminal.category).toUpperCase().intern();
				String word = terminal.word;
				terminal.ann = StatParser.buildWordDefn (doc, word, span, constit, pennPOS);
			}
			if (nameConstitEnd == terminalEnd)
				nameConstitEnd = -1;
		}
		// prune parse tree:  remove a node if it has no word or children
		pruneTree (node);
		determineNonTerminalSpans(node, treeSpan.start());
		// add head links
		if (hr == null)
			hr = HeadRule.createDefaultRule();
		hr.apply (node);
		// add annotations for non-terminals:
		Jet.Parser.ParseTreeNode.makeParseAnnotations(doc, node);
	}
	
	/**
	 *  recursively traverse parse tree <CODE>node</CODE>, removing terminal nodes
	 *  which are not associated with any word, and any non-terminal nodes all
	 *  of whose children have been removed.
	 *  <P>
	 *  This method is used by <CODE>setJetAnnotations</CODE> to prune a parse
	 *  tree after multiple NNP nodes for a multi-word name have been replaced by
	 *  a single NAME node.
	 */
	
	private ParseTreeNode pruneTree(ParseTreeNode node) {
		ParseTreeNode[] children = node.children;
		if (children != null) {
			ArrayList<ParseTreeNode> newChildren = new ArrayList<ParseTreeNode>();
			for (ParseTreeNode child : children) {
				ParseTreeNode c = pruneTree(child);
				if (c != null) newChildren.add(c);
			}
			if (newChildren.isEmpty()) {
				children = null;
			} else {
				children = newChildren.toArray(new ParseTreeNode[0]);
			}
			node.children = children;
		}
		if (node.word == null && children == null)
			return null;
		else
			return node;
	}  
	
}
