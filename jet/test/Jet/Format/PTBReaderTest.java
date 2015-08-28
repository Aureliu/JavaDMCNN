package Jet.Format;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import junit.framework.JUnit4TestAdapter;

import org.junit.ComparisonFailure;
import org.junit.Test;

import Jet.Parser.ParseTreeNode;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class PTBReaderTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(PTBReaderTest.class);
	}

	@Test
	public void testParseSingleSentence() throws Exception {
		PTBReader parser = new PTBReader();

		String source = "( (S \n" + "    (NP-SBJ \n" + "      (NP (NNP Pierre) (NNP Vinken) )\n"
				+ "      (, ,) \n" + "      (ADJP \n" + "        (NP (CD 61) (NNS years) )\n"
				+ "        (JJ old) )\n" + "      (, ,) )" + "    (VP (MD will) \n"
				+ "      (VP (VB join) \n" + "        (NP (DT the) (NN board) )\n"
				+ "        (PP-CLR (IN as) \n"
				+ "          (NP (DT a) (JJ nonexecutive) (NN director) ))\n"
				+ "        (NP-TMP (NNP Nov.) (CD 29) )))\n" + "    (. .) ))\n";

		String expected = "<constit cat=\"s\"><constit cat=\"np\" func=\"sbj\"><constit cat=\"np\"><constit cat=\"nnp\">Pierre </constit><constit cat=\"nnp\">Vinken</constit></constit><constit cat=\",\">, </constit><constit cat=\"adjp\"><constit cat=\"np\"><constit cat=\"cd\">61 </constit><constit cat=\"nns\">years </constit></constit><constit cat=\"jj\">old</constit></constit><constit cat=\",\">, </constit></constit><constit cat=\"vp\"><constit cat=\"md\">will </constit><constit cat=\"vp\"><constit cat=\"vb\">join </constit><constit cat=\"np\"><constit cat=\"dt\">the </constit><constit cat=\"nn\">board </constit></constit><constit cat=\"pp\" func=\"clr\"><constit cat=\"in\">as </constit><constit cat=\"np\"><constit cat=\"dt\">a </constit><constit cat=\"jj\">nonexecutive </constit><constit cat=\"nn\">director </constit></constit></constit><constit cat=\"np\" func=\"tmp\"><constit cat=\"nnp\">Nov. </constit><constit cat=\"cd\">29</constit></constit></constit></constit><constit cat=\".\">.</constit></constit>";
		StringReader in = new StringReader(source);
		Document doc = parser.load(in).getDocument();
		removeChildren(doc);
		
		String actual = doc.writeSGML("constit").toString().replaceAll("\n", "");

		try {
			assertEquals(expected, actual);
			assertEquals(1, doc.annotationsOfType("sentence").size());
		} catch (ComparisonFailure ex) {
			System.out.println(ex.getExpected());
			System.out.println(ex.getActual());
			throw ex;
		}
	}

	@Test
	public void testParseMultiSentence() throws Exception {
		PTBReader parser = new PTBReader();

		String source = "( (S (NP-SBJ (DT This) ) (VBZ is) (NP (DT a) (NN pen) ) ) )";
		source += '\n';
		source += "( (S (NP-SBJ (DT That) ) (VBZ are) (NP (DT a) (NN bike) ) ) )";

		String expected1 = "<constit cat=\"s\"><constit cat=\"np\" func=\"sbj\"><constit cat=\"dt\">This </constit></constit><constit cat=\"vbz\">is </constit><constit cat=\"np\"><constit cat=\"dt\">a </constit><constit cat=\"nn\">pen</constit></constit></constit>";
		String expected2 = "<constit cat=\"s\"><constit cat=\"np\" func=\"sbj\"><constit cat=\"dt\">That </constit></constit><constit cat=\"vbz\">are </constit><constit cat=\"np\"><constit cat=\"dt\">a </constit><constit cat=\"nn\">bike</constit></constit></constit>";

		Document doc = parser.load(new StringReader(source)).getDocument();
		removeChildren(doc);
		List<Annotation> sentences = doc.annotationsOfType("sentence");
		assertNotNull(sentences);
		assertEquals(2, sentences.size());

		String actual1 = doc.writeSGML("constit", sentences.get(0).span()).toString().replaceAll(
				"\n", "");
		String actual2 = doc.writeSGML("constit", sentences.get(1).span()).toString().replaceAll(
				"\n", "");

		assertEquals(expected1, actual1);
		assertEquals(expected2, actual2);
	}

	@Test
	public void testNone() throws Exception {
		String source = "( (S \n" + "    (NP-SBJ \n"
				+ "      (NP (NNP Zenith) (NNP Data) (NNPS Systems) (NNP Corp.) )\n"
				+ "      (, ,) \n" + "      (NP \n" + "        (NP (DT a) (NN subsidiary) )\n"
				+ "        (PP (IN of) \n"
				+ "          (NP (NNP Zenith) (NNP Electronics) (NNP Corp.) )))\n"
				+ "      (, ,) )\n" + "    (VP (VBD received) \n" + "      (NP \n"
				+ "        (NP (DT a) \n" + "          (ADJP \n"
				+ "            (QP ($ $) (CD 534) (CD million) )\n"
				+ "            (-NONE- *U*) )\n" + "          (NNP Navy) (NN contract) )\n"
				+ "        (PP (IN for) \n" + "          (NP \n"
				+ "            (NP (NN software) \n" + "              (CC and)\n"
				+ "              (NNS services) )\n" + "            (PP (IN of) \n"
				+ "              (NP (NNS microcomputers) ))\n"
				+ "            (PP-TMP (IN over) \n"
				+ "              (NP (DT an) (JJ 84-month) (NN period) ))))))\n" + "    (. .) ))\n";
		String expected = "<constit cat=\"s\"><constit cat=\"np\" func=\"sbj\"><constit cat=\"np\"><constit cat=\"nnp\">Zenith </constit><constit cat=\"nnp\">Data </constit><constit cat=\"nnps\">Systems </constit><constit cat=\"nnp\">Corp.</constit></constit><constit cat=\",\">, </constit><constit cat=\"np\"><constit cat=\"np\"><constit cat=\"dt\">a </constit><constit cat=\"nn\">subsidiary </constit></constit><constit cat=\"pp\"><constit cat=\"in\">of </constit><constit cat=\"np\"><constit cat=\"nnp\">Zenith </constit><constit cat=\"nnp\">Electronics </constit><constit cat=\"nnp\">Corp.</constit></constit></constit></constit><constit cat=\",\">, </constit></constit><constit cat=\"vp\"><constit cat=\"vbd\">received </constit><constit cat=\"np\"><constit cat=\"np\"><constit cat=\"dt\">a </constit><constit cat=\"adjp\"><constit cat=\"qp\"><constit cat=\"$\">$ </constit><constit cat=\"cd\">534 </constit><constit cat=\"cd\">million </constit></constit></constit><constit cat=\"nnp\">Navy </constit><constit cat=\"nn\">contract </constit></constit><constit cat=\"pp\"><constit cat=\"in\">for </constit><constit cat=\"np\"><constit cat=\"np\"><constit cat=\"nn\">software </constit><constit cat=\"cc\">and </constit><constit cat=\"nns\">services </constit></constit><constit cat=\"pp\"><constit cat=\"in\">of </constit><constit cat=\"np\"><constit cat=\"nns\">microcomputers </constit></constit></constit><constit cat=\"pp\" func=\"tmp\"><constit cat=\"in\">over </constit><constit cat=\"np\"><constit cat=\"dt\">an </constit><constit cat=\"jj\">84-month </constit><constit cat=\"nn\">period</constit></constit></constit></constit></constit></constit></constit><constit cat=\".\">.</constit></constit>";
		PTBReader parser = new PTBReader();
		Document doc = parser.load(new StringReader(source)).getDocument();
		removeChildren(doc);

		List<Annotation> sentences = doc.annotationsOfType("sentence");
		assertNotNull(sentences);
		assertEquals(1, sentences.size());

		String actual = doc.writeSGML("constit").toString().replaceAll("\n", "");
		assertEquals(expected, actual);
	}

	@Test
	public void testProcessBackslash() throws Exception {
		String source = "( (S\n"
				+ "    (NP-SBJ\n"
				+ "      (NP (NNP Robert) (NNP Stovall) )\n"
				+ "      (, ,)\n"
				+ "      (NP\n"
				+ "        (NP (DT a) (JJ veteran) (NNP New) (NNP York) (NN money) (NN manager) )\n"
				+ "        (CC and)\n" + "        (NP\n" + "          (NP (NN president) )\n"
				+ "          (PP (IN of)\n"
				+ "            (NP (NNP Stovall\\/Twenty-First) (NNP Securities) ))))\n"
				+ "      (, ,) )\n" + "    (VP (VBZ has)\n" + "      (NP (NN money) )\n"
				+ "      (PP-LOC (IN in)\n" + "        (NP (CC both)\n"
				+ "          (NP (NN gold) )\n" + "          (CC and)\n"
				+ "          (NP (NN utility) (NNS issues) ))))\n" + "    (. .) ))\n";

		String expected = "<constit cat=\"s\"><constit cat=\"np\" func=\"sbj\"><constit cat=\"np\"><constit cat=\"nnp\">Robert </constit><constit cat=\"nnp\">Stovall</constit></constit><constit cat=\",\">, </constit><constit cat=\"np\"><constit cat=\"np\"><constit cat=\"dt\">a </constit><constit cat=\"jj\">veteran </constit><constit cat=\"nnp\">New </constit><constit cat=\"nnp\">York </constit><constit cat=\"nn\">money </constit><constit cat=\"nn\">manager </constit></constit><constit cat=\"cc\">and </constit><constit cat=\"np\"><constit cat=\"np\"><constit cat=\"nn\">president </constit></constit><constit cat=\"pp\"><constit cat=\"in\">of </constit><constit cat=\"np\"><constit cat=\"nnp\">Stovall/Twenty-First </constit><constit cat=\"nnp\">Securities</constit></constit></constit></constit></constit><constit cat=\",\">, </constit></constit><constit cat=\"vp\"><constit cat=\"vbz\">has </constit><constit cat=\"np\"><constit cat=\"nn\">money </constit></constit><constit cat=\"pp\" func=\"loc\"><constit cat=\"in\">in </constit><constit cat=\"np\"><constit cat=\"cc\">both </constit><constit cat=\"np\"><constit cat=\"nn\">gold </constit></constit><constit cat=\"cc\">and </constit><constit cat=\"np\"><constit cat=\"nn\">utility </constit><constit cat=\"nns\">issues</constit></constit></constit></constit></constit><constit cat=\".\">.</constit></constit>";

		PTBReader parser = new PTBReader();
		Document doc = parser.load(new StringReader(source)).getDocument();
		removeChildren(doc);
		String actual = doc.writeSGML("constit").toString().replaceAll("\n", "");

		assertEquals("annotate result missmatch", expected, actual);
	}

	@Test
	public void testRoundBrace() throws Exception {
		String source = "(NP (DT an) (-LRB- -LRB-) (VBG offending) (-RRB- -RRB-) (NN country))";
		String expected = "an (offending) country";
		PTBReader parser = new PTBReader();
		Document doc = parser.load(new StringReader(source)).getDocument();
		assertEquals(expected + "\n", doc.text());
	}

	@Test
	public void testCurlyBrace() throws Exception {
		String source = "(NP (DT an) (-LRB- -LCB-) (VBG offending) (-RRB- -RCB-) (NN country))";
		String expected = "an {offending} country";
		PTBReader parser = new PTBReader();
		Document doc = parser.load(new StringReader(source)).getDocument();
		assertEquals(expected + "\n", doc.text());
	}

	@Test
	public void testSquareBrace() throws Exception {
		String source = "(NP (DT an) (-LRB- -LSB-) (VBG offending) (-RRB- -RSB-) (NN country))";
		String expected = "an [offending] country";
		PTBReader parser = new PTBReader();
		Document doc = parser.load(new StringReader(source)).getDocument();
		assertEquals(expected + "\n", doc.text());
	}

	@Test
	public void testShortenedForm() throws Exception {
		String source = "(NP (NN today) (POS 's))";
		String expected = "today's";
		PTBReader parser = new PTBReader();
		Document doc = parser.load(new StringReader(source)).getDocument();
		assertEquals(expected + "\n", doc.text());
	}

	@Test
	public void testAddAnnotations() throws Exception {
		String sentence = "The warning had its effect .";
		String ptb = "(S1 (S (NP (DT The) (NN warning)) (VP (AUX had) (NP (PRP$ its) (NN effect))) (. .)))";
		String expected = "<constit cat=\"s1\"><constit cat=\"s\"><constit cat=\"np\"><constit cat=\"dt\">The </constit><constit cat=\"nn\">warning </constit></constit><constit cat=\"vp\"><constit cat=\"aux\">had </constit><constit cat=\"np\"><constit cat=\"prp$\">its </constit><constit cat=\"nn\">effect </constit></constit></constit><constit cat=\".\">.</constit></constit></constit>";

		Document doc = new Document(sentence);
		PTBReader reader = new PTBReader();
		List<ParseTreeNode> parseTrees = reader.loadParseTrees(new StringReader(ptb));
		assertNotNull(parseTrees);
		assertEquals(1, parseTrees.size());
		reader.addAnnotations(parseTrees.get(0), doc, doc.fullSpan(), false);
		assertEquals(expected, doc.writeSGML("constit").toString().replaceAll("\n", ""));
	}

	@Test
	public void testAddAnnotationsList() throws Exception {
		String source = "<sentence>That is the difference. </sentence>\n"
				+ "<sentence>The warning had its effect. </sentence>";
		String ptb = "(S1 (S (NP (DT That)) (VP (AUX is) (NP (DT the) (NN difference))) (. .)))"
				+ "(S1 (S (NP (DT The) (NN warning)) (VP (AUX had) (NP (PRP$ its) (NN effect))) (. .)))";

		String expected1 = "<constit cat=\"s1\"><constit cat=\"s\"><constit cat=\"np\"><constit cat=\"dt\">That </constit></constit><constit cat=\"vp\"><constit cat=\"aux\">is </constit><constit cat=\"np\"><constit cat=\"dt\">the </constit><constit cat=\"nn\">difference</constit></constit></constit><constit cat=\".\">.</constit></constit></constit>";
		String expected2 = "<constit cat=\"s1\"><constit cat=\"s\"><constit cat=\"np\"><constit cat=\"dt\">The </constit><constit cat=\"nn\">warning </constit></constit><constit cat=\"vp\"><constit cat=\"aux\">had </constit><constit cat=\"np\"><constit cat=\"prp$\">its </constit><constit cat=\"nn\">effect</constit></constit></constit><constit cat=\".\">.</constit></constit></constit>";

		Document doc = new Document(source);
		doc.annotateWithTag("sentence");
		List<Annotation> sentences = (List<Annotation>) doc.annotationsOfType("sentence");
		assertNotNull(sentences);
		assertEquals(2, sentences.size());
		PTBReader reader = new PTBReader();

		List<ParseTreeNode> parseTrees = reader.loadParseTrees(new StringReader(ptb));
		assertNotNull(parseTrees);
		assertEquals(2, parseTrees.size());
		reader.addAnnotations(parseTrees, doc, "sentence", doc.fullSpan(), false);

		assertEquals("First sentence", expected1, doc.writeSGML("constit", sentences.get(0).span())
				.toString().replaceAll("\n", ""));
		assertEquals("Second sentence", expected2, doc
				.writeSGML("constit", sentences.get(1).span()).toString().replaceAll("\n", ""));
	}

	@Test
	public void testNoneNode() throws Exception {
		String source = "(S (NP A) (-NONE- *) (NP B) )";

		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));
		Document doc = treebank.getDocument();
		assertEquals("A B\n", doc.text());
	}

	@Test
	public void testEmptyNonTerminalNode() throws Exception {
		String source = "(S (NNP Java) (S (-NONE- *) ) )";
		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));
		checkSpans(treebank.getParseTree(0));
	}
	
	@Test
	public void testGapIndex() throws Exception {
		String source = "( (NP=2 ($ $) (CD 340,000) (-NONE- *U*) ) )";
		String result = "<constit cat=\"np\"><constit cat=\"$\">$ </constit><constit cat=\"cd\">340,000</constit></constit>";
		PTBReader reader = new PTBReader();
		Treebank treebank = reader.load(new StringReader(source));
		Document doc = treebank.getDocument();
		removeChildren(doc);
		assertEquals("$ 340,000", doc.text().toString().trim());
		doc.setSGMLwrapMargin(0);
		assertEquals(result, doc.writeSGML("constit").toString().replaceAll("\n", ""));
	}
	
	@Test
	public void testBackslash() throws Exception {
		String source = "(S1 (FRAG (PP (IN By) (NP (NNP Susan) (NNP \\))) (. .)))";
		PTBReader reader = new PTBReader();
		reader.setBackslashAsEscapeCharacter(false);
		Treebank treebank = reader.load(new StringReader(source));
		Document doc = treebank.getDocument();
		assertEquals("By Susan \\.\n", doc.text());
	}

	private void checkSpans(ParseTreeNode node) {
		assertTrue(String.format("Invalid span: [%d-%d]", node.start, node.end), node.start <= node.end);
		if (node.children != null) {
			for (ParseTreeNode child : node.children) {
				checkSpans(child);
			}
		}
	}
	
	private static void removeChildren(Document doc) {
		Vector<Annotation> constits = doc.annotationsOfType("constit");
		for (Annotation constit : constits) {
			constit.remove("children");
		}
	}
}
