// -*- tab-width: 4 -*-
package Jet.NE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import AceJet.AceDocument;
import AceJet.AceEntity;
import AceJet.AceEntityMention;
import Jet.HMM.HMMNameTagger;
import Jet.HMM.HMMTagger;
import Jet.HMM.WordFeatureHMMemitter;
import Jet.Lex.Tokenizer;
import Jet.Lisp.FeatureSet;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

/**
 * Evaluation tool for named entity extraction.
 *
 * @author Akira ODA
 */
public class Evaluate {
	private File inputDir;

	private File outputDir;

	private HMMTagger hmmTagger;

	private HMMNameTagger hmmNameTagger;

	private NameAnnotator annotator = new NameAnnotator();

	private String format;

	private String target;

	private int countOfExtracted;

	private int countOfGold;

	private int countOfExactMatch;

	private int countOfPositionMatch;

	public Evaluate(String inputDirectory, String outputDirectory,
			Properties props) throws IOException, RuleFormatException {
		this.inputDir = new File(inputDirectory);
		this.outputDir = new File(outputDirectory);

		if (!inputDir.isDirectory()) {
			throw new IllegalArgumentException(
					"inputDirectory must be directory");
		}

		if (outputDir.exists()) {
			if (!outputDir.isDirectory()) {
				throw new IllegalArgumentException(
						"inputDirectory must be directory");
			}
		} else {
			outputDir.mkdirs();
		}

		init(props);
	}

	private void init(Properties props) throws IOException, RuleFormatException {
		String dictTrie = props.getProperty("ne.dict.trie");
		String dictCdb = props.getProperty("ne.dict.cdb");
		String neHierarchyFile = props.getProperty("ne.hierarchy");
		String hmmPosFile = props.getProperty("hmm.model.pos");
		String hmmNameFile = props.getProperty("hmm.model.ne");
		String ruleFile = props.getProperty("ne.rule");
		String neMapFile = props.getProperty("ne.map");
		target = props.getProperty("target");
		format = props.getProperty("format");

		String[] checkParamNames = { "ne.dict.trie", "ne.dict.cdb",
				"ne.hierarchy", "hmm.model.pos", "ne.rule", "format", "target" };
		for (String name : checkParamNames) {
			String value = props.getProperty(name);
			if (value == null || value.trim().length() == 0) {
				String message = name + " must be specified";
				throw new RuntimeException(message);
			}
		}

		Dictionary dict = new TrieDictionary(dictTrie, dictCdb);
		annotator.setDictionary(dict);

		hmmTagger = new HMMTagger();
		hmmTagger.load(hmmPosFile);

		if (hmmNameFile != null) {
			hmmNameTagger = new HMMNameTagger(WordFeatureHMMemitter.class);
			hmmNameTagger.load(hmmNameFile);
			hmmNameTagger.nameHMM.newDocument();
		}

		annotator.loadRules(new File(ruleFile));
		annotator.loadClassHierarchy(new File(neHierarchyFile));

		if (neMapFile != null) {
			Map<String, String> neMap = loadNamedEntityMap(neMapFile);
			annotator.setAliasMap(neMap);
		}
	}

	public void run() throws IOException {
		countOfGold = 0;
		countOfExtracted = 0;
		countOfExactMatch = 0;
		countOfPositionMatch = 0;

		for (File file : inputDir.listFiles()) {
			String filename = file.getPath();
			if (!filename.endsWith(".sgm") && !filename.endsWith(".sgml")) {
				continue;
			}

			Document doc = null;

			if (format.equalsIgnoreCase("sgml")) {
				doc = readSGML(file);
			} else if (format.equalsIgnoreCase("sgml+apf")) {
				File apfFile = new File(file.getParent(), file.getName()
						+ ".apf.xml");
				doc = readSGMLAndAPF(file, apfFile);
			} else {
				throw new RuntimeException("Illegal format : " + format);
			}

			processDocument(doc, file);
		}
		System.out.println("total (exactMatch) :");
		System.out.printf("recall    : %.2f", percentage(countOfExactMatch,
				countOfGold));
		System.out.println();
		System.out.printf("precision : %.2f", percentage(countOfExactMatch,
				countOfExtracted));
		System.out.println();

		System.out.println("total: (positionMatch)");
		System.out.printf("recall    : %.2f", percentage(countOfPositionMatch,
				countOfGold));
		System.out.println();
		System.out.printf("precision : %.2f", percentage(countOfPositionMatch,
				countOfExtracted));
		System.out.println();
		System.out.println();
	}

	private double percentage(int count, int total) {
		return (double) count / total * 100;
	}

	private void processDocument(Document taggedDoc, File file)
			throws IOException {

		Document doc = new Document(taggedDoc.text());
		doc.annotateWithTag(target);
		List<Annotation> texts = doc.annotationsOfType(target);
		for (Annotation text : texts) {
			Tokenizer.tokenize(doc, text.span());
			hmmTagger.tagJet(doc, text.span());
			if (hmmNameTagger != null) {
				hmmNameTagger.tag(doc, text.span());
			}
			annotator.annotate(doc, text.span());
		}

		SortedMap<Span, String> gold = extractEntities(taggedDoc);
		SortedMap<Span, String> extracted = extractEntities(doc);

		File goldOut = new File(outputDir, file.getName() + ".gold");
		File systemOut = new File(outputDir, file.getName() + ".out");

		writeEntities(taggedDoc, gold, new PrintStream(goldOut));
		writeEntities(doc, extracted, new PrintStream(systemOut));

		int exactMatch = 0;
		int positionMatch = 0;
		for (Map.Entry<Span, String> entry : extracted.entrySet()) {
			String val = gold.get(entry.getKey());
			if (val != null) {
				positionMatch++;
				if (entry.getValue().equals(val)) {
					exactMatch++;
				}
			}
		}

		System.out.println(file.getName() + " (exactMatch) :");
		System.out.printf("recall:    %.2f",
				percentage(exactMatch, gold.size()));
		System.out.println();
		System.out.printf("precision: %.2f", percentage(exactMatch, extracted
				.size()));
		System.out.println();
		System.out.println(file.getName() + " (positionMatch) :");
		System.out.printf("recall:    %.2f", percentage(positionMatch, gold
				.size()));
		System.out.println();
		System.out.printf("precision: %.2f", percentage(positionMatch,
				extracted.size()));
		System.out.println();

		countOfGold += gold.size();
		countOfExactMatch += exactMatch;
		countOfExtracted += extracted.size();
		countOfPositionMatch += positionMatch;
	}

	private void writeEntities(Document doc, SortedMap<Span, String> entities,
			PrintStream out) {
		for (Map.Entry<Span, String> entry : entities.entrySet()) {
			String word = doc.normalizedText(entry.getKey()).replaceAll("\\s+",
					" ");
			String type = entry.getValue();
			out.print(word);
			out.print(' ');
			out.print(type);
			out.println();
		}
	}

	private Document readSGML(File file) throws IOException {
		FileInputStream fin = null;
		BufferedReader in = null;

		fin = new FileInputStream(file);
		in = new BufferedReader(new InputStreamReader(fin, "ISO-8859-1"));
		String line;
		StringBuilder buffer = new StringBuilder();

		while ((line = in.readLine()) != null) {
			buffer.append(line);
			buffer.append('\n');
		}

		Pattern textTagPattern = makeTargetTagPattern(target);
		Matcher textMatcher = textTagPattern.matcher(buffer);

		Pattern tagPattern = Pattern.compile("<(.*?)>(\\s*)(.*?)</\\1>(\\s*)",
				Pattern.DOTALL);
		Matcher tagMatcher = tagPattern.matcher(buffer);

		StringBuilder text = new StringBuilder();
		SortedMap<Span, String> entities = new TreeMap<Span, String>();

		int lastMatchedOffset = 0;

		while (textMatcher.find()) {
			text.append(buffer, lastMatchedOffset, textMatcher.start(1));
			tagMatcher.region(textMatcher.start(), textMatcher.end(1));

			int lastTagMatchedOffset = textMatcher.start();
			while (tagMatcher.find()) {
				text.append(buffer, lastTagMatchedOffset, tagMatcher.start());
				String type = tagMatcher.group(1);
				String headingSpaces = tagMatcher.group(2);
				String word = tagMatcher.group(3);
				String trailingSpaces = tagMatcher.group(4);

				int start = text.length() + headingSpaces.length();
				int end = start + word.length() + trailingSpaces.length();
				entities.put(new Span(start, end), type);

				text.append(headingSpaces);
				text.append(word);
				text.append(trailingSpaces);
				lastTagMatchedOffset = tagMatcher.end();
			}

			text.append(buffer, lastTagMatchedOffset, textMatcher.end());
			lastMatchedOffset = textMatcher.end();
		}

		text.append(buffer, lastMatchedOffset, buffer.length());

		Document doc = new Document(text.toString());
		for (Map.Entry<Span, String> entry : entities.entrySet()) {
			FeatureSet attrs = new FeatureSet();
			attrs.put("TYPE", entry.getValue());
			doc.annotate("ENAMEX", entry.getKey(), attrs);
		}

		return doc;
	}

	private Document readSGMLAndAPF(File sgml, File apf) {
		AceDocument.ace2004 = false;
		AceDocument.ace2005 = true;

		AceDocument aceDoc = new AceDocument(sgml.getPath(), apf.getPath());
		Document doc = aceDoc.JetDocument();
		List<AceEntity> entities = aceDoc.entities;

		String originalText = aceDoc.JetDocument().text();
		int[] map = computeOffsetMap(originalText);
		for (AceEntity entity : entities) {
			for (AceEntityMention mention : (List<AceEntityMention>) entity.mentions) {
				if (mention.type.equals("NAME")) {
					int start = map[mention.getJetHead().start()];
					int end = map[mention.getJetHead().end()];

					FeatureSet attrs = new FeatureSet();
					attrs.put("TYPE", entity.type);
					Span span = new Span(start, end);
					doc.annotate("ENAMEX", span, attrs);
				}
			}
		}

		return doc;
	}

	private int[] computeOffsetMap(CharSequence text) {
		int len = text.length();
		int[] map = new int[len];
		boolean inTag = false;
		int pos = 0;

		for (int i = 0; i < len; i++) {
			switch (text.charAt(i)) {
			case '<':
				inTag = true;
				break;

			case '>':
				inTag = false;
				break;

			default:
				if (!inTag) {
					map[pos++] = i;
				}
			}
		}

		int[] result = new int[pos];
		System.arraycopy(map, 0, result, 0, pos);
		return result;
	}

	private SortedMap<Span, String> extractEntities(Document doc) {
		List<Annotation> list = doc.annotationsOfType("ENAMEX");
		SortedMap<Span, String> entities = new TreeMap<Span, String>();
		if (list != null) {
			for (Annotation a : list) {
				String type = (String) a.get("TYPE");
				entities.put(a.span(), type);
			}
		}

		return entities;
	}

	private Map<String, String> loadNamedEntityMap(String filename)
			throws IOException {
		FileInputStream fin = null;
		BufferedReader in = null;
		Map<String, String> map = new HashMap<String, String>();

		try {
			fin = new FileInputStream(filename);
			in = new BufferedReader(new InputStreamReader(fin, "ISO-8859-1"));
			String line;

			while ((line = in.readLine()) != null) {
				String[] tmp = line.split("\\s+", 2);
				assert (tmp.length == 2);
				map.put(tmp[0], tmp[1]);
			}
		} finally {
			if (in != null) {
				in.close();
			} else if (fin != null) {
				fin.close();
			}
		}

		return map;
	}

	private Pattern makeTargetTagPattern(String type) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("<");
		buffer.append(Pattern.quote(type));
		buffer.append(">(.*?)");
		buffer.append("</");
		buffer.append(Pattern.quote(type));
		buffer.append(">");

		return Pattern.compile(buffer.toString(), Pattern.DOTALL);
	}

	public static void main(String[] args) throws IOException,
			RuleFormatException {
		if (args.length != 3) {
			usage();
			System.exit(-1);
		}

		String propFile = args[0];
		String inputDir = args[1];
		String outputDir = args[2];

		Properties props = new Properties();
		try {
			props.load(new FileInputStream(propFile));
		} catch (IOException ex) {
			System.err.println(ex.getMessage());
			System.exit(1);
		}

		Evaluate eval = new Evaluate(inputDir, outputDir, props);
		eval.run();
	}

	private static void usage() {
		System.err.printf("Usage: java %s propertyFile inputDir outputDir\n",
				Evaluate.class.getName());
	}
}
