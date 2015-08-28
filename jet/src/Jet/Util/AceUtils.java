// -*- tab-width: 4 -*-
package Jet.Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import Jet.Lisp.FeatureSet;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.ExternalDocument;
import Jet.Tipster.Span;

/**
 * Utility class for dealing ACE annotation file.
 *
 * @author Akira ODA
 */
public class AceUtils {
	private static final String TEXT_SEGMENT_TAG_NAME = "TEXT";

	private static final DocumentBuilderFactory builderFactory = DocumentBuilderFactory
			.newInstance();

	private static final Map<String, String> typeMap;

	private static final Map<String, NameAnnotator> annotators;

	static {
		Map<String, String> m = new HashMap<String, String>();
		// XXX: VEH (vehicle) and WEA (weapon) is not converted.
		m.put("GSP", "GPE");
		m.put("PER", "PERSON");
		m.put("ORG", "ORGANIZATION");
		m.put("LOC", "LOCATION");
		m.put("FAC", "FACILITY");
		typeMap = Collections.unmodifiableMap(m);

		Map<String, NameAnnotator> a = new HashMap<String, NameAnnotator>();
		a.put("apf.v5.1.1.dtd", Ace2005and2004NameAnnotator.getInstance());
		a.put("apf.v4.0.1.dtd", Ace2005and2004NameAnnotator.getInstance());
		a.put("ace-rdc.v2.0.1.dtd", Ace2003NameAnnotator.getInstance());
		a.put("ace-pilot-ref.dtd", Ace2001NameAnnotator.getInstance());
		annotators = Collections.unmodifiableMap(a);
	}

	/**
	 * Reads annotation information written in APF format and construct
	 * <code>Jet.Tipster.Document</code> object.
	 *
	 * @param file
	 * @return
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public static Document loadAnnotatedDocument(File file) throws ParserConfigurationException,
			SAXException, IOException {
		DocumentBuilder parser = builderFactory.newDocumentBuilder();
		parser.setEntityResolver(new ACEEntityResolver());
		Element root = parser.parse(file).getDocumentElement();
		File sourceFile = getSourceFile(file.getParentFile(), root);
		ExternalDocument doc = new ExternalDocument("sgml", sourceFile.getPath());
		doc.setAllTags(true);
		if (!doc.open()) {
			throw new IOException();
		}

		DocumentType docType = root.getOwnerDocument().getDoctype();

		NameAnnotator annotator = annotators.get(docType.getSystemId());
		if (annotator != null) {
			annotator.annotate(doc, root);
		} else {
			throw new RuntimeException("DOCTYPE " + docType.getSystemId() + " is not supported.");
		}
		return doc;
	}

	/**
	 * Reads annotation information files written in APF format and constructs
	 * <code>Jet.Tipster.Document</code> objects from a specified directory.
	 *
	 * @param dir
	 * @return
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	public static Collection<Document> loadAnnotatedDocumentsFromDirectory(File dir)
			throws ParserConfigurationException, SAXException, IOException {
		List<Document> docs = new ArrayList<Document>();

		for (File file : dir.listFiles()) {
			if (file.getName().toLowerCase().endsWith(".apf.xml")) {
				docs.add(loadAnnotatedDocument(file));
			}
		}

		return docs;
	}

	private static File getSourceFile(File base, Element root) {
		File source = new File(base, root.getAttribute("URI"));
		if (source.exists()) {
			return source;
		}

		String docid = getChild(root, "document").getAttribute("DOCID");
		source = new File(base, docid + ".sgm");
		if (source.exists()) {
			return source;
		}

		return null;
	}

	public static void writeNamedEntities(Document doc, Writer out)
			throws ParserConfigurationException, TransformerFactoryConfigurationError,
			TransformerException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();

		org.w3c.dom.Document dom = builder.newDocument();

		String id = doc.normalizedText((Annotation) doc.annotationsOfType("DOCNO").get(0));
		Element source_file = dom.createElement("source_file");
		dom.appendChild(source_file);
		source_file.setAttribute("TYPE", "text");
		source_file.setAttribute("VERSION", "1.2");
		source_file.setAttribute("URI", id);
		Element document = dom.createElement("document");
		source_file.appendChild(document);
		document.setAttribute("DOCID", id);

		List<Annotation> names = doc.annotationsOfType("ENAMEX");
		int index = 1;
		for (Annotation name : names) {
			Element entity = dom.createElement("entity");
			document.appendChild(entity);
			entity.setAttribute("ID", id + "-" + index);
			Element entityType = dom.createElement("entity_type");
			entity.appendChild(entityType);
			entityType.appendChild(dom.createTextNode((String) name.get("TYPE")));

			Element entityMention = dom.createElement("entity_mention");
			entity.appendChild(entityMention);
			entityMention.setAttribute("TYPE", "NAME");

			Element head = dom.createElement("head");
			entityMention.appendChild(head);
			head.appendChild(createCharseqElement(dom, doc, name.span()));

			Element extent = dom.createElement("extent");
			entityMention.appendChild(extent);
			extent.appendChild(createCharseqElement(dom, doc, name.span()));

			index++;
		}

		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		Source source = new DOMSource(dom);
		Result result = new StreamResult(out);
		transformer.transform(source, result);
	}

	private static Element createCharseqElement(org.w3c.dom.Document dom, Document doc, Span span) {
		int start = span.start();
		int end = span.end() - 1;

		while (end > 0 && Character.isWhitespace(doc.charAt(end))) {
			end--;
		}

		Element charseq = dom.createElement("charseq");
		Element startElement = dom.createElement("start");
		charseq.appendChild(startElement);
		startElement.appendChild(dom.createTextNode(Integer.toString(start)));
		Element endElement = dom.createElement("end");
		charseq.appendChild(endElement);
		endElement.appendChild(dom.createTextNode(Integer.toString(end)));

		return charseq;
	}

	private static final int skipWhitespace(Document doc, Span span, int offset) {
		int end = span.end();
		while (offset < end && Character.isWhitespace(doc.charAt(offset))) {
			offset++;
		}

		return offset;
	}

	private static List<Element> getChildren(Element element, String name) {
		NodeList children = element.getChildNodes();
		List<Element> elements = new ArrayList<Element>();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element childElement = (Element) node;
				if (name == null || name.equals(childElement.getTagName())) {
					elements.add(childElement);
				}
			}
		}

		return elements;
	}

	private static Element getChild(Element element, String tagName) {
		NodeList children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element child = (Element) node;
				if (child.getTagName().equals(tagName)) {
					return child;
				}
			}
		}

		return null;
	}

	public static void main(String[] args) throws Exception {
		File dir = new File("../corpus/test");
		Collection<Document> docs = loadAnnotatedDocumentsFromDirectory(dir);
		for (Document doc : docs) {
			doc.setSGMLwrapMargin(0);
			System.out.println(doc.writeSGML(null));
		}
	}

	/**
	 * Annotator interface for named entity described in APF format.
	 *
	 * @author Akira ODA
	 */
	private static interface NameAnnotator {
		/**
		 * Annotates named entity for each named entity.
		 *
		 * @param doc
		 *            document to be annotated named entity
		 * @param root
		 *            root element of APF xml document
		 */
		public void annotate(Document doc, Element root);
	}

	/**
	 * Concrete class of NameAnnotator for ACE2004 and ACE2005.
	 *
	 * @author Akira ODA
	 */
	private static class Ace2005and2004NameAnnotator implements NameAnnotator {
		private static Ace2005and2004NameAnnotator instance = new Ace2005and2004NameAnnotator();

		public static Ace2005and2004NameAnnotator getInstance() {
			return instance;
		}

		public void annotate(Document doc, Element root) {
			Element document = getChild(root, "document");
			List<Element> entities = getChildren(document, "entity");

			List<Annotation> textSegments = doc.annotationsOfType(TEXT_SEGMENT_TAG_NAME);
			Annotation textSegment = textSegments.get(0);

			for (Element entity : entities) {
				String type = entity.getAttribute("TYPE");
				if (typeMap.containsKey(type)) {
					type = typeMap.get(type);
				}

				List<Element> entityMentions = getChildren(entity, "entity_mention");
				for (Element entityMention : entityMentions) {
					String nameType = entityMention.getAttribute("TYPE");
					if (nameType.equals("NAM")) {
						Element head = getChild(entityMention, "head");
						Element charseq = getChild(head, "charseq");

						int start = Integer.parseInt(charseq.getAttribute("START"));
						int end = Integer.parseInt(charseq.getAttribute("END"));
						end = skipWhitespace(doc, textSegment.span(), end + 1);

						FeatureSet attrs = new FeatureSet();
						attrs.put("TYPE", type);
						doc.annotate("ENAMEX", new Span(start, end), attrs);
					}
				}
			}
		}
	}

	/**
	 * Concrete class of NameAnnotator for ACE2003
	 *
	 * @author Akira ODA
	 */
	private static class Ace2003NameAnnotator implements NameAnnotator {
		private static Ace2003NameAnnotator instance = new Ace2003NameAnnotator();

		public static Ace2003NameAnnotator getInstance() {
			return instance;
		}

		public void annotate(Document doc, Element root) {
			Element document = getChild(root, "document");
			List<Element> entities = getChildren(document, "entity");
			List<Annotation> textSegments = doc.annotationsOfType("TEXT");
			Annotation textSegment = textSegments.get(0);

			for (Element entity : entities) {
				String entityType = getChild(entity, "entity_type").getTextContent();
				if (typeMap.containsKey(entityType)) {
					entityType = typeMap.get(entityType);
				}
				List<Element> entityMentions = getChildren(entity, "entity_mention");
				for (Element entityMention : entityMentions) {
					String type = entityMention.getAttribute("TYPE");
					if (type.equals("NAME")) {
						Element head = getChild(entityMention, "head");
						Element charseq = getChild(head, "charseq");
						String startText = getChild(charseq, "start").getTextContent();
						String endText = getChild(charseq, "end").getTextContent();
						int start = Integer.parseInt(startText);
						int end = Integer.parseInt(endText) + 1;
						end = skipWhitespace(doc, textSegment.span(), end);

						FeatureSet fs = new FeatureSet();
						fs.put("TYPE", entityType);
						doc.annotate("ENAMEX", new Span(start, end), fs);
					}
				}
			}
		}
	}

	/**
	 * Concrete class of NameAnnotator for ACE2003
	 *
	 * @author Akira ODA
	 */
	private static class Ace2001NameAnnotator implements NameAnnotator {
		private static final Ace2001NameAnnotator instance = new Ace2001NameAnnotator();

		public static Ace2001NameAnnotator getInstance() {
			return instance;
		}

		public void annotate(Document doc, Element root) {
			List<Annotation> textSegments = doc.annotationsOfType("TEXT");

			assert textSegments != null;
			assert textSegments.size() == 1;

			Annotation textSegment = textSegments.get(0);

			Element document = getChild(root, "document");
			List<Element> entities = getChildren(document, "entity");
			for (Element entity : entities) {
				String entityType = getChild(entity, "entity_type").getTextContent();
				if (typeMap.containsKey(entityType)) {
					entityType = typeMap.get(entityType);
				}

				List<Element> entityMentions = getChildren(entity, "entity_mention");
				for (Element entityMention : entityMentions) {
					String type = entityMention.getAttribute("TYPE");
					if (!type.equals("NAME")) {
						continue;
					}

					Element head = getChild(entityMention, "head");
					Element charseq = getChild(head, "charseq");
					String startText = getChild(charseq, "start").getTextContent();
					String endText = getChild(charseq, "end").getTextContent();

					int start = Integer.parseInt(startText);
					int end = Integer.parseInt(endText) + 1;
					end = skipWhitespace(doc, textSegment.span(), end);

					FeatureSet attrs = new FeatureSet();
					attrs.put("TYPE", entityType);
					doc.annotate("ENAMEX", new Span(start, end), attrs);
				}
			}
		}
	}

	/**
	 * EntityResolver for ACE DOCTYPEs.
	 *
	 * @author Akira ODA
	 */
	private static class ACEEntityResolver implements EntityResolver {
		public InputSource resolveEntity(String publicId, String systemId) {
			ClassLoader loader = this.getClass().getClassLoader();
			String path = "Jet/Util/dtd/" + basename(systemId);
			InputStream in = loader.getResourceAsStream(path);
			if (in == null) {
				System.out.println("not found");
				return null;
			} else {
				return new InputSource(in);
			}
		}

		/**
		 * Returns basename of uri.
		 *
		 * @param uri
		 * @return
		 */
		private static String basename(String uri) {
			int index = uri.lastIndexOf("/");
			if (index >= 0) {
				return uri.substring(index + 1);
			} else {
				return uri;
			}
		}
	}
}
