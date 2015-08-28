/**
 * 
 */
package Jet.Chunk;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.JUnit4TestAdapter;

import org.junit.Test;

import Jet.Format.InvalidFormatException;
import Jet.Format.PTBReader;
import Jet.Format.Treebank;
import Jet.Lisp.FeatureSet;
import Jet.Parser.ParseTreeNode;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;
import Jet.Util.IOUtils;

/**
 * @author oda
 * 
 */
public class TreeBasedChunkerTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(TreeBasedChunkerTest.class);
	}

	/**
	 * {@link Jet.Chunk.TreeBasedChunker#chunk(Jet.Tipster.Document, Jet.Parser.ParseTreeNode)}
	 */
	@Test
	public void testSimpleChunk() throws IOException, InvalidFormatException {
		String s = "( (S \n" + "    (NP-SBJ \n" + "      (NP (NNP Pierre) (NNP Vinken) )\n"
				+ "      (, ,) \n" + "      (ADJP \n" + "        (NP (CD 61) (NNS years) )\n"
				+ "        (JJ old) )\n" + "      (, ,) )\n" + "    (VP (MD will) \n"
				+ "      (VP (VB join) \n" + "        (NP (DT the) (NN board) )\n"
				+ "        (PP-CLR (IN as) \n"
				+ "          (NP (DT a) (JJ nonexecutive) (NN director) ))\n"
				+ "        (NP-TMP (NNP Nov.) (CD 29) )))\n" + "    (. .) ))\n";
		TreeBasedChunker chunker = new TreeBasedChunker();
		List<String> expectedTags = Arrays
				.asList("B-NP I-NP O B-NP I-NP B-ADJP O B-VP I-VP B-NP I-NP B-PP B-NP I-NP I-NP B-NP I-NP O"
						.split(" "));

		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(s));

		Document doc = treebank.getDocument();
		List<ParseTreeNode> trees = treebank.getParseTreeList();
		assertEquals(1, trees.size());
		ParseTreeNode tree = trees.get(0);
		chunker.chunk(doc, tree);
		List<String> actualTags = tokenizeAndAddChunkTag(doc, tree);

		assertEquals(expectedTags.size(), actualTags.size());
		assertEquals(expectedTags, actualTags);
	}

	@Test
	public void testEmptySInVP() throws Exception {
		String source = "( (S \n"
				+ "    (S-TPC-2 \n"
				+ "      (NP-SBJ-1 \n"
				+ "        (NP (DT The) (NN percentage) )\n"
				+ "        (PP (IN of) \n"
				+ "          (NP (NN lung) (NN cancer) (NNS deaths) ))\n"
				+ "        (PP-LOC (IN among) \n"
				+ "          (NP \n"
				+ "            (NP (DT the) (NNS workers) )\n"
				+ "            (PP-LOC (IN at) \n"
				+ "              (NP (DT the) \n"
				+ "                (NAC-LOC (NNP West) (NNP Groton) \n"
				+ "                  (, ,)\n"
				+ "                  (NNP Mass.) \n"
				+ "                  (, ,)\n"
				+ "                  )\n"
				+ "                (NN paper) (NN factory) )))))\n"
				+ "      (VP (VBZ appears) \n"
				+ "        (S \n"
				+ "          (NP-SBJ (-NONE- *-1) )\n"
				+ "          (VP (TO to) \n"
				+ "            (VP (VB be) \n"
				+ "              (NP-PRD \n"
				+ "                (NP (DT the) (JJS highest) )\n"
				+ "                (PP (IN for) \n"
				+ "                  (NP \n"
				+ "                    (NP (DT any) (NN asbestos) (NNS workers) )\n"
				+ "                    (RRC \n"
				+ "                      (VP (VBN studied) \n"
				+ "                        (NP (-NONE- *) )\n"
				+ "                        (PP-LOC (IN in) \n"
				+ "                          (NP (JJ Western) (VBN industrialized) (NNS countries) ))))))))))))\n"
				+ "    (, ,) \n" + "    (NP-SBJ (PRP he) )\n" + "    (VP (VBD said) \n"
				+ "      (SBAR (-NONE- 0) \n" + "        (S (-NONE- *T*-2) )))\n"
				+ "    (. .) ))\n";

		List<String> expectedTags = Arrays.asList("B-NP", "I-NP", "B-PP", "B-NP", "I-NP", "I-NP",
				"B-PP", "B-NP", "I-NP", "B-PP", "B-NP", "I-NP", "I-NP", "I-NP", "I-NP", "I-NP",
				"I-NP", "I-NP", "B-VP", "I-VP", "I-VP", "B-NP", "I-NP", "B-PP", "B-NP", "I-NP",
				"I-NP", "B-VP", "B-PP", "B-NP", "I-NP", "I-NP", "O", "B-NP", "B-VP", "O");

		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));
		Document doc = treebank.getDocument();
		assertEquals(1, treebank.getParseTreeList().size());
		ParseTreeNode tree = treebank.getParseTree(0);

		TreeBasedChunker chunker = new TreeBasedChunker();
		chunker.chunk(doc, tree);

		List<String> actualTags = tokenizeAndAddChunkTag(doc, tree);
		assertEquals(expectedTags, actualTags);
	}

	@Test
	public void testPruneInFrontOfHead() throws Exception {
		String source = "( (S \n"
				+ "    (NP-SBJ-10 \n"
				+ "      (NP (NNP J.P.) (NNP Bolduc) )\n"
				+ "      (, ,) \n"
				+ "      (NP \n"
				+ "        (NP (NN vice) (NN chairman) )\n"
				+ "        (PP (IN of) \n"
				+ "          (NP \n"
				+ "            (NP (NNP W.R.) (NNP Grace) (CC &) (NNP Co.) )\n"
				+ "            (, ,) \n"
				+ "            (SBAR \n"
				+ "              (WHNP-10 (WDT which) )\n"
				+ "              (S \n"
				+ "                (NP-SBJ (-NONE- *T*-10) )\n"
				+ "                (VP (VBZ holds) \n"
				+ "                  (NP \n"
				+ "                    (NP (DT a) \n"
				+ "                      (ADJP (CD 83.4) (NN %) )\n"
				+ "                      (NN interest) )\n"
				+ "                    (PP-LOC (IN in) \n"
				+ "                      (NP (DT this) (JJ energy-services) (NN company) )))))))))\n"
				+ "      (, ,) )\n" + "    (VP (VBD was) \n" + "      (VP (VBN elected) \n"
				+ "        (S \n" + "          (NP-SBJ (-NONE- *-10) )\n"
				+ "          (NP-PRD (DT a) (NN director) ))))\n" + "    (. .) ))\n";

		List<String> expectedTags = Arrays.asList("B-NP", "I-NP", "O", "B-NP", "I-NP", "B-PP",
				"B-NP", "I-NP", "I-NP", "I-NP", "O", "B-NP", "B-VP", "B-NP", "I-NP", "I-NP",
				"I-NP", "B-PP", "B-NP", "I-NP", "I-NP", "O", "B-VP", "I-VP", "B-NP", "I-NP", "O");

		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));
		Document doc = treebank.getDocument();

		assertEquals(1, treebank.getParseTreeList().size());
		ParseTreeNode tree = treebank.getParseTree(0);

		TreeBasedChunker chunker = new TreeBasedChunker();
		chunker.chunk(doc, tree);

		List<String> actualTags = tokenizeAndAddChunkTag(doc, tree);

		assertEquals(expectedTags, actualTags);
	}

	@Test
	public void testPOS() throws Exception {
		String source = "( (S \n" + "    (NP-SBJ \n" + "      (NP (JJ Average) (NN maturity) )\n"
				+ "      (PP (IN of) \n" + "        (NP \n"
				+ "          (NP (DT the) (NNS funds) (POS ') )\n"
				+ "          (NNS investments) )))\n" + "    (VP (VBD lengthened) \n"
				+ "      (PP-EXT (IN by) \n" + "        (NP (DT a) (NN day) ))\n"
				+ "      (PP-DIR (TO to) \n" + "        (NP \n"
				+ "          (NP (CD 41) (NNS days) )\n" + "          (, ,) \n"
				+ "          (NP \n" + "            (NP (DT the) (JJS longest) )\n"
				+ "            (PP-TMP (IN since) \n"
				+ "              (NP (JJ early) (NNP August) )))\n" + "          (, ,) ))\n"
				+ "      (PP (VBG according) \n" + "        (PP (TO to) \n"
				+ "          (NP (NNP Donoghue) (POS 's) ))))\n" + "    (. .) ))\n";
		List<String> expectedTags = Arrays.asList("B-NP", "I-NP", "B-PP", "B-NP", "I-NP", "B-NP",
				"I-NP", "B-VP", "B-PP", "B-NP", "I-NP", "B-PP", "B-NP", "I-NP", "O", "B-NP",
				"I-NP", "B-PP", "B-NP", "I-NP", "O", "B-PP", "B-PP", "B-NP", "B-NP", "O");

		PTBReader reader = new PTBReader();
		TreeBasedChunker chunker = new TreeBasedChunker();
		Treebank treebank = reader.load(new StringReader(source));
		Document doc = treebank.getDocument();

		assertEquals(1, treebank.getParseTreeList().size());
		ParseTreeNode tree = treebank.getParseTree(0);
		chunker.chunk(doc, tree);
		List<String> actualTags = tokenizeAndAddChunkTag(doc, tree);

		assertEquals(expectedTags, actualTags);
	}

	@Test
	public void testAux() throws Exception {
		String source = "(\n" + " (S\n" + "  (NP (NN Sentence))\n" + "  (VP\n" + "   (MD must)\n"
				+ "   (VP\n" + "    (VP (VB include) (NP (NN noun) (NN phrase)))\n"
				+ "    (CC and)\n"
				+ "    (VP (AUX be) (VP (VBN ended) (PP (IN with) (NP (NN period)))))))\n"
				+ "  (. .)))";
		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));

		Document doc = treebank.getDocument();
		ParseTreeNode tree = treebank.getParseTree(0);
		TreeBasedChunker chunker = new TreeBasedChunker();
		chunker.chunk(doc, tree);

		List<Annotation> chunks = doc.annotationsOfType("chunk");
		assertEquals(6, chunks.size());
		assertChunk("NP", "Sentence", chunks.get(0), doc);
		assertChunk("VP", "must include", chunks.get(1), doc);
		assertChunk("NP", "noun phrase", chunks.get(2), doc);
		assertChunk("VP", "be ended", chunks.get(3), doc);
		assertChunk("PP", "with", chunks.get(4), doc);
		assertChunk("NP", "period", chunks.get(5), doc);
	}

	private static List<String> tokenizeAndAddChunkTag(Document doc, ParseTreeNode tree) {
		List<Annotation> chunks = doc.annotationsOfType("chunk");
		List<ParseTreeNode> terminals = collectTerminals(tree);

		if (chunks == null) {
			chunks = Collections.emptyList();
		}

		for (ParseTreeNode terminal : terminals) {
			Span span = new Span(terminal.start, terminal.end);
			doc.annotate("token", span, new FeatureSet("chunk", "O"));
		}

		for (Annotation chunk : chunks) {
			List<Annotation> tokens = doc.annotationsOfType("token", chunk.span());
			String chunkType = (String) chunk.get("type");
			boolean first = true;
			for (Annotation token : tokens) {
				if (first) {
					token.put("chunk", String.format("B-%s", chunkType));
					first = false;
				} else {
					token.put("chunk", String.format("I-%s", chunkType));
				}
			}
		}

		List<Annotation> tokens = doc.annotationsOfType("token", new Span(tree.start, tree.end));
		List<String> tags = new ArrayList<String>(tokens.size());
		for (Annotation token : tokens) {
			tags.add((String) token.get("chunk"));
		}

		return tags;
	}

	private static List<ParseTreeNode> collectTerminals(ParseTreeNode node) {
		if (node.children == null) {
			return Collections.singletonList(node);
		}

		List<ParseTreeNode> list = new ArrayList<ParseTreeNode>();
		for (ParseTreeNode child : node.children) {
			list.addAll(collectTerminals(child));
		}
		return list;
	}
	
	private static void assertChunk(String type, String text, Annotation chunk, Document doc) {
		assertEquals(type, chunk.get("type"));
		assertEquals(text, doc.normalizedText(chunk));
	}

	public static void main(String[] args) throws Exception {
		File inputDirectory = new File("ptb");
		File outputDirectory = new File("chunk");

		if (!outputDirectory.exists()) {
			outputDirectory.mkdirs();
		}

		TreeBasedChunker chunker = new TreeBasedChunker();

		PTBReader reader = new PTBReader();
		for (File file : inputDirectory.listFiles()) {
			System.out.println(file);
			Treebank treebank = reader.load(file);
			Document doc = treebank.getDocument();

			String filename = file.getName();
			File outFile = new File(outputDirectory, filename.replaceAll("\\.mrg", ".chunk"));
			StringBuilder builder = new StringBuilder();

			for (ParseTreeNode tree : treebank.getParseTreeList()) {
				chunker.chunk(doc, tree);
				List<String> chunkTags = tokenizeAndAddChunkTag(doc, tree);
				List<Annotation> tokens = doc.annotationsOfType("token", new Span(tree.start,
						tree.end));
				for (int i = 0; i < chunkTags.size(); i++) {
					String token = doc.text(tokens.get(i)).trim();
					builder.append(String.format("%-7s %s", chunkTags.get(i), token));
					builder.append("\n");
				}
				builder.append("\n");
			}

			IOUtils.writeFile(outFile, "UTF-8", builder.toString());
		}
	}
}
