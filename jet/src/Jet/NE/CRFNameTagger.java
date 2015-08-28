// -*- tab-width: 4 -*-
package Jet.NE;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import Jet.Lex.EnglishLex;
import Jet.Lex.Tokenizer;
import Jet.Lisp.FeatureSet;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.DocumentCollection;
import Jet.Tipster.ExternalDocument;
import Jet.Tipster.Span;
import Jet.Util.IOUtils;
import Jet.Zoner.SentenceSplitter;
import Jet.Zoner.SpecialZoner;
import edu.umass.cs.mallet.base.fst.CRF3;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.pipe.SerialPipes;
import edu.umass.cs.mallet.base.pipe.TokenSequence2FeatureVectorSequence;
import edu.umass.cs.mallet.base.pipe.iterator.PipeInputIterator;
import edu.umass.cs.mallet.base.pipe.tsf.TokenText;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.types.Sequence;
import edu.umass.cs.mallet.base.types.TokenSequence;
import edu.umass.cs.mallet.base.util.PropertyList;

public class CRFNameTagger {
	private static final String PUNCTUATIONS = "[,\\.;:?!()]";

	private static final String QUOTES = "[\"`']";

	private static final String OPEN_PAREN = "[\\[({]";

	private static final String CLOSE_PAREN = "[\\])}]";

	private CRF3 crf;

	private List<Pipe> features = new ArrayList<Pipe>();

	private PropertyList properties;

	public CRFNameTagger() {
		addFeatures();
	}

	public void train(Collection<Document> docs) {
		Pipe pipe = createPipe();
		CRF3 crf = new CRF3(pipe, null);
		InstanceList trainingData = new InstanceList(pipe);
		for (Document doc : docs) {
			PipeInputIterator iter = new DocumentToSentenceIterator(doc, "TEXT", trainingData
					.size() + 1);

			while (iter.hasNext()) {
				Instance carrier = iter.nextInstance();
				carrier.setPropertyList(properties);
				trainingData.add(carrier.getPipedCopy(pipe));
			}
		}

		crf.addStatesForLabelsConnectedAsIn(trainingData);
		crf.train(trainingData);
		this.crf = crf;
	}

	public void annotate(Document doc, Span span) {
		Pipe pipe = crf.getInputPipe();
		Instance carrier = new Instance(span, null, "sentence", doc);
		carrier.setPropertyList(properties);
		carrier = pipe.pipe(carrier);
		Sequence input = (Sequence) carrier.getData();

		Sequence labels = (Sequence) crf.viterbiPath(input).output();
		List<Annotation> tokens = doc.annotationsOfType("token", span);

		assert tokens.size() == input.size();
		assert tokens.size() == labels.size();

		int pos = 0;
		while (pos < tokens.size()) {
			String label = (String) labels.get(pos);
			if (!label.startsWith("B-")) {
				pos++;
				continue;
			}

			int start = tokens.get(pos).start();
			int end;
			String iLabel = "I-" + label.substring(2);
			pos++;
			while (pos < tokens.size() && labels.get(pos).equals(iLabel)) {
				pos++;
			}
			end = tokens.get(pos - 1).end();
			FeatureSet attrs = new FeatureSet();
			attrs.put("TYPE", label.substring(2));
			doc.annotate("ENAMEX", new Span(start, end), attrs);
		}
	}

	public void setProperty(String key, Object value) {
		properties = PropertyList.add(key, value, properties);
	}

	protected Pipe createPipe() {
		Pipe[] pipes = new Pipe[3];
		pipes[0] = new SentenceToTokenSequencePipe();
		pipes[1] = createFeaturePipe();
		pipes[2] = new TokenSequence2FeatureVectorSequence();
		return new SerialPipes(pipes);
	}

	protected Pipe createFeaturePipe() {
		Pipe[] pipes = features.toArray(new Pipe[features.size()]);
		return new SerialPipes(pipes);
	}

	protected void addFeatures() {
		addFeature(new FirstWordFeature("FIRST_WORD"));
		addFeature(new NumericalFeatures("NUMERICAL"));

		addFeature(new RegexpMatchFeature("INITCAP", "\\p{Lu}.*"));
		addFeature(new RegexpMatchFeature("CAPITALIZED", "\\p{Lu}\\p{Ll}*"));
		addFeature(new RegexpMatchFeature("ALLCAPS", "\\p{Lu}+"));
		addFeature(new RegexpMatchFeature("ALLDIGITS", "[0-9]+"));
		addFeature(new RegexpMatchFeature("TWO_DIGITS", "[0-9]{2}"));
		addFeature(new RegexpMatchFeature("FOUR_DIGITS", "[0-9]{4}"));
		addFeature(new RegexpMatchFeature("MORETHANFOURDIGITS", "[0-9]{5,}"));
		addFeature(new RegexpMatchFeature("ROMAN_NUMBER", "[IXV]+"));
		addFeature(new RegexpMatchFeature("CAPITALANDDIGIT", "[A-Z0-9]+"));
		addFeature(new RegexpMatchFeature("YEAR_DECADE", "(?:[0-9]{2})?[0-9]{2}s"));
		addFeature(new RegexpMatchFeature("MIXEDCAPS", "\\p{Lu}\\p{Ll}+\\p{Lu}.*"));
		addFeature(new RegexpMatchFeature("MULTIDOT", "\\.\\.+"));
		addFeature(new RegexpMatchFeature("ENDSINDOT", "[^\\.].*\\."));
		addFeature(new RegexpMatchFeature("CONTAINSDASH", "\\w+-\\w*"));
		addFeature(new RegexpMatchFeature("ACRONYM", "\\p{Lu}[\\p{Lu}\\.]\\.[\\p{Lu}\\.]"));
		addFeature(new RegexpMatchFeature("CAP_OTHER_PERIOD", "[A-Z].+\\."));
		addFeature(new RegexpMatchFeature("CAP_PERIOD", "[A-Z]\\."));
		addFeature(new RegexpMatchFeature("SINGLECHAR", "."));
		addFeature(new RegexpMatchFeature("CAPLETTER", "[A-Z]"));
		addFeature(new RegexpMatchFeature("PUNCTUATION", PUNCTUATIONS));
		addFeature(new RegexpMatchFeature("QUOTE", QUOTES));
		addFeature(new AlphaFeature("a="));
		addFeature(new NonAlphaFeature("A="));
		addFeature(new PatternFeature("p="));
		addFeature(new SummarizedPatternFeature("P="));
		addFeature(new TokenText("W="));
		addFeature(new TokenLowerText("w="));

		addFeature(new RegexpMatchFeature("CURRENCY", "\\p{Sc}"));
		addFeature(new LexiconCategoryFeature("CAT="));

		addFeature(new RegexpMatchFeature("OPEN_PAREN", OPEN_PAREN));
		addFeature(new RegexpMatchFeature("CLOSE_PAREN", CLOSE_PAREN));

		addFeature(new NamedEntityInDictionaryFeature("NE="));
	}

	public void addFeature(Pipe feature) {
		features.add(feature);
	}

	public void writeModel(OutputStream out) throws IOException {
		ObjectOutputStream objOut = new ObjectOutputStream(out);
		objOut.writeObject(crf);
	}

	public void writeModel(File file) throws IOException {
		OutputStream out = new FileOutputStream(file);
		try {
			writeModel(out);
		} finally {
			IOUtils.closeQuietly(out);
		}
	}

	public void readModel(InputStream in) throws IOException, ClassNotFoundException {
		ObjectInputStream objIn = new ObjectInputStream(in);
		crf = (CRF3) objIn.readObject();
	}

	public void readModel(File file) throws IOException, ClassNotFoundException {
		InputStream in = new FileInputStream(file);
		try {
			readModel(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			usage();
		}

		File modelFile = new File(args[1]);
		File list = new File(args[2]);

		EnglishLex.readLexicon("data/Jet4.dict");

		if (args[0].equals("train")) {
			if (args.length != 3) {
				usage();
			}
			train(modelFile, list);
		} else if (args[0].equals("test")) {
			if (args.length != 4) {
				usage();
			}

			File outDir = new File(args[3]);
			test(modelFile, list, outDir);
		} else {
			usage();
		}
	}

	private static void usage() {
		System.err.printf("usage: java %s train|test args", CRFNameTagger.class.getName());
		System.err.println();
		System.err.println();
		System.err.println("train parameters: ");
		System.err.println("    modelFilename targetDirectory");
		System.err.println();
		System.err.println("test parameters: ");
		System.err.println("    modelFilename targetDirectory outputDirectory");
		System.exit(1);
	}

	private static void train(File modelFile, File list) throws IOException,
			ParserConfigurationException, SAXException {
		Collection<Document> docs = loadDocumentCollection(list);
		prepareDocuments(docs);
		Dictionary dict = loadDictionary();

		CRFNameTagger tagger = new CRFNameTagger();
		tagger.setProperty("dictionary", dict);
		tagger.train(docs);
		tagger.writeModel(modelFile);
	}

	public static void test(File modelFile, File list, File outDir) throws IOException,
			ParserConfigurationException, SAXException, ClassNotFoundException {

		Collection<Document> docs = loadDocumentCollection(list);
		prepareDocuments(docs);

		Dictionary dict = loadDictionary();
		CRFNameTagger tagger = new CRFNameTagger();
		tagger.setProperty("dictionary", dict);
		tagger.readModel(modelFile);

		InstanceList testingData = new InstanceList(tagger.crf.getInputPipe());
		for (Document doc : docs) {
			List<Annotation> sentences = doc.annotationsOfType("sentence");
			for (Annotation sentence : sentences) {
				Instance carrier = new Instance(sentence.span(), null, "sentence", doc);
				carrier.setPropertyList(tagger.properties);
				carrier = carrier.getPipedCopy(tagger.crf.getInputPipe());
				testingData.add(carrier);
			}
		}

		PrintStream out = null;

		try {
			out = new PrintStream(new File(outDir, "tokens.txt"));
			for (int i = 0; i < testingData.size(); i++) {
				Instance carrier = testingData.getInstance(i);
				TokenSequence tokens = (TokenSequence) carrier.getSource();
				Sequence expected = (Sequence) carrier.getTarget();
				Sequence input = (Sequence) carrier.getData();
				Sequence actual = tagger.crf.transduce(input);

				assert tokens.size() == actual.size();
				assert tokens.size() == expected.size();

				for (int j = 0; j < tokens.size(); j++) {
					out.printf("%-20s %15s %15s", tokens.getToken(j).getText(), expected.get(j),
							actual.get(j));
					out.println();
				}
				out.println();
			}
		} finally {
			IOUtils.closeQuietly(out);
		}

		Evaluator evaluator = new Evaluator();
		for (Document gold : docs) {
			Document system = new Document(gold);
			system.removeAnnotationsOfType("ENAMEX");
			List<Annotation> sentences = system.annotationsOfType("sentence");
			for (Annotation sentence : sentences) {
				tagger.annotate(system, sentence.span());
			}
			evaluator.evaluate(system, gold);
		}

		System.out.printf("%-15s\t%10s\t%10s", "type", "precision", "recall");
		System.out.println();
		for (String type : evaluator.getTypes()) {
			double precision = evaluator.getPrecision(type);
			double recall = evaluator.getRecall(type);
			System.out.printf("%-15s\t%10.2f\t%10.2f", type, precision, recall);
			System.out.println();
		}
		System.out.printf("%-15s\t%10.2f\t%10.2f", "TOTAL", evaluator.getPrecision(), evaluator
				.getRecall());
		System.out.println();

		for (Document doc : docs) {
			doc.removeAnnotationsOfType("ENAMEX");
		}

		for (Document doc : docs) {
			List<Annotation> sentences = doc.annotationsOfType("sentence");
			for (Annotation sentence : sentences) {
				tagger.annotate(doc, sentence.span());
			}

			String id = getId(doc);

			doc.removeAnnotationsOfType("token");
			doc.setSGMLwrapMargin(0);
			File docOutFile = new File(outDir, id + ".sgm");
			Writer docOut = null;

			try {
				docOut = new BufferedWriter(new FileWriter(docOutFile));
				docOut.append(doc.writeSGML(null));
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			} finally {
				IOUtils.closeQuietly(docOut);
			}
		}
	}

	private static String getId(Document doc) {
		List<Annotation> docId = doc.annotationsOfType("DOCNO");

		if (docId != null && docId.size() != 0) {
			return doc.normalizedText(docId.get(0));
		}

		docId = doc.annotationsOfType("DOCID");
		if (docId != null && docId.size() != 0) {
			return doc.normalizedText(docId.get(0));
		}

		return null;
	}

	private static Collection<Document> loadDocumentCollection(File listFile) {
		DocumentCollection collection = new DocumentCollection(listFile.getPath());
		List<Document> docs = new ArrayList<Document>();
		if (!collection.open()) {
			return null;
		}

		for (int i = 0; i < collection.size(); i++) {
			ExternalDocument doc = collection.get(i);
			doc.setAllTags(true);
			if (!doc.open()) {
				return null;
			}

			docs.add(doc);
		}
		return docs;
	}

	/**
	 * Adds annotations for named entity detection.
	 *
	 * @param docs
	 */
	private static void prepareDocuments(Collection<Document> docs) {
		for (Document doc : docs) {
			SpecialZoner.findSpecialZones(doc);
			List<Annotation> textSegments = doc.annotationsOfType("TEXT");
			for (Annotation text : textSegments) {
				SentenceSplitter.split(doc, text.span());
			}

			List<Annotation> sentences = doc.annotationsOfType("sentence");
			for (Annotation sentence : sentences) {
				Tokenizer.tokenize(doc, sentence.span());
			}

			doc.removeAnnotationsOfType("textBreak");
			doc.removeAnnotationsOfType("dateline");
		}
	}

	private static Dictionary loadDictionary() throws IOException {
		Dictionary dict = new TrieDictionary("data/wsj.ned.da", "data/wsj.ned.cdb");
		return dict;
	}
}
