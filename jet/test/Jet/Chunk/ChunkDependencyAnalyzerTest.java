package Jet.Chunk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import junit.framework.JUnit4TestAdapter;

import org.junit.Before;
import org.junit.Test;

import Jet.Format.InvalidFormatException;
import Jet.Format.PTBReader;
import Jet.Format.Treebank;
import Jet.Parser.HeadRule;
import Jet.Parser.ParseTreeNode;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class ChunkDependencyAnalyzerTest {
	private ChunkDependencyAnalyzer analyzer;

	private PTBReader ptbReader;
	
	private HeadRule headRule;
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(ChunkDependencyAnalyzerTest.class);
	}

	@Before
	public void setUp() throws Exception {
		analyzer = new ChunkDependencyAnalyzer();
		ptbReader = new PTBReader();
		headRule = HeadRule.getDefaultRule();
	}

	@Test
	public void testAnalyzeChunkDependency() throws Exception {
		String ptb = "( (S (NP (DT the) (NN company) (NN president)) "
				+ "(VP (VBD had) (NP (DT a) (JJ private) (NN plane) (. .)))))";
		Treebank treebank = parsePTB(ptb);
		Document doc = treebank.getDocument();
		ParseTreeNode tree = treebank.getParseTree(0);
		headRule.apply(tree);
		analyzer.analyzeChunkDependency(doc, tree);
		
		List<Annotation> chunks = doc.annotationsOfType("chunk");
		assertEquals(3, chunks.size());
		assertTrue(chunks.get(0).get("dep") == chunks.get(1));
		assertTrue(chunks.get(1).get("dep") == null);
		assertTrue(chunks.get(2).get("dep") == chunks.get(1));
	}
	
	@Test
	public void testAnalyzeChunkDependency2() throws Exception {
		String ptb = "(S1 (S (NP (PRP You)) (VP (MD should) (RB n't) (VP (VB depend) (PP (IN on) (NP (JJ other) (NNS people))))) (. .)))";
		Treebank treebank = parsePTB(ptb);
		Document doc = treebank.getDocument();
		ParseTreeNode tree = treebank.getParseTree(0);
		headRule.apply(tree);
		analyzer.analyzeChunkDependency(doc, tree);
		List<Annotation> chunks = doc.annotationsOfType("chunk");
		assertEquals(4, chunks.size());
		assertTrue(chunks.get(0).get("dep") == chunks.get(1));
		assertTrue(chunks.get(1).get("dep") == null);
		assertTrue(chunks.get(2).get("dep") == chunks.get(1));
		assertTrue(chunks.get(3).get("dep") == chunks.get(2));
	}
	
	public Treebank parsePTB(String src) throws IOException, InvalidFormatException {
		return ptbReader.load(new StringReader(src));
	}
}
