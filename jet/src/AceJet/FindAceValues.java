// -*- tab-width: 4 -*-
package AceJet;

/**
 *   identifies phrases which are Ace Values.
 */

import java.io.*;
import java.util.*;

import Jet.JetTest;
import Jet.Parser.SynFun;
import Jet.Refres.Resolve;
import Jet.Tipster.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindAceValues {

	final static Logger logger = LoggerFactory.getLogger(FindAceValues.class);

	static TreeMap<String, String> valueTypeMap = new TreeMap<String, String>();

	/**
	 *  reads the Value type dictionary from the file specified by Jet
	 *  parameter <CODE>Ace.Value.fileName</CODE>.
	 */

	public static void readTypeDict () {
		String fileName = JetTest.getConfigFile("Ace.Value.fileName");
		if (fileName != null) {
			readTypeDict(fileName);
		} else {
			logger.error ("No Ace.Value.fileName specified in config file");
		}
	}

	/**
	 *  reads the Value type dictionary from file <CODE>dictFile</CODE>.
	 */

	public static void readTypeDict (String dictFile) {
		logger.info ("Loading type dictionary {}", dictFile);
		valueTypeMap = new TreeMap<String, String>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(dictFile));
			String line;
			String type = null;
			while ((line = reader.readLine()) != null) {
				if (line.trim().length() == 0) continue;
				if (line.charAt(0) == '#') continue;
				if (line.charAt(0) == '=') {
					type = line.substring(1);
				} else {
					String[] tokens = line.split(" ");
					if (type == null) {
						logger.warn ("Error in loading value dictionary.");
						logger.warn ("No type specified for {}",tokens[0]);
					} else {
						valueTypeMap.put(tokens[0], type);
					}
				}
			}
			logger.info ("Value dictionary loaded.");
		} catch (IOException e) {
			logger.error ("Unable to load value dictionary due to exception: {}", e);
		}
	}

	/**
	 *  returns <CODE>true</CODE> if a value type dictionary has been loaded.
	 */

	public static boolean isDictLoaded () {
		return !valueTypeMap.isEmpty();
	}

	/**
	 *  returns the AceValue type and subtype of a mention:  Numeric, Crime,
	 *  Sentence, Contact-Info, ...  A return value of "OTHER" indicates that
	 *  it is not an AceValue.
	 */

	public static String getTypeSubtype (Document doc, Annotation mention) {
		String typeSubtype;
		String paHead = SynFun.getHead(doc, mention).toLowerCase();
		Annotation headC = Resolve.getHeadC (mention);
		String headWord = Resolve.normalizeName(doc.text(headC).trim());
		String name = SynFun.getName(doc, mention);
		String cat = (String) headC.get("cat");
		// named mentions are not values, except for email and url
		if (name != null) {
			if (paHead.equals("email"))
				return "Contact-Info:E-Mail";
			else if (paHead.equals("url"))
				return "Contact-Info:URL";
			else return "OTHER";
		}
		// nor are pronouns
		if (cat.equals("pro") || cat.equals("det") || cat.equals("q"))
			return "OTHER";
		// for all other nouns, look head up in value type dictionary
		//    first use actual (inflected) head
		typeSubtype = lookUpValueType(headWord);
		if (typeSubtype != null)
			return typeSubtype.intern();
		//    then try with regularized head from PA structure (singular form of plural word)
		typeSubtype = lookUpValueType(paHead);
		if (typeSubtype != null)
			return typeSubtype.intern();
		return "OTHER";
	}

	static String lookUpValueType (String word) {
		return valueTypeMap.get(word.toLowerCase());
	}

	public static String bareType (String typeSubtype) {
		int p = typeSubtype.indexOf(':');
		if (p > 0)
			return typeSubtype.substring(0,p);
		else
			return typeSubtype;
	}

	static String subtype (String typeSubtype) {
		int p = typeSubtype.indexOf(':');
		if (p > 0)
			return typeSubtype.substring(p+1);
		else
			return "";
	}

	/**
	 *  adds to AceDocument <CODE>aceDoc</CODE> the Ace Values contained in
	 *  Document <CODE>doc</CODE>.  This method assumes that reference resolution
	 *  has added <B>entity</B> annotations to the document.  All Crimes,
	 *  Sentences, and Job-Titles are added;  ones which are not referenced will
	 *  later be deleted by <CODE>pruneAceValues</CODE>.
	 */

	public static void buildAceValues (Document doc, String docId, AceDocument aceDoc) {
		int valueCount = 0;
		String docText = doc.text();
		// get entities
		Vector entities = doc.annotationsOfType("entity");
		if (entities != null) {
			for (int ientity=0; ientity<entities.size(); ientity++) {
				Annotation entity = (Annotation) entities.get(ientity);
				// get mentions
				Vector mentions = (Vector) entity.get("mentions");
				for (int imention=0; imention<mentions.size(); imention++) {
					Annotation mention = (Annotation) mentions.get(imention);
					// for each mention, getTypeSubtpe
					String typeSubtype = getTypeSubtype (doc, mention);
					// if not "OTHER", create AceValueMention and AceValue
					if (!typeSubtype.equals("OTHER")) {
						String valueId = docId + "-V" + (++valueCount);
						buildAceValue (valueId, typeSubtype, mention.span(), aceDoc, docText);
					}
				}
			}
		}
		logger.info ("Built {} values.", aceDoc.values.size());
	}

	/**
	 *  constructs an AceValue and adds it to the AceDocument.
	 *  @param  id       the ID of the AceValue
	 *  @param  typeSubtype the type and subtype of the Value
	 *  @param  extent   the extent of the Value in the Document
	 *  @param  aceDoc   the AceDocument to which the Value is added
	 *  @param  filetext the text of the Document
	 */

	public static void buildAceValue (String id, String typeSubtype,
			Span extent, AceDocument aceDoc, String fileText) {
		// separate type and subtype
		String type = bareType(typeSubtype);
		String subtype = subtype(typeSubtype);
		// generate value Mention
		AceValueMention mention = new AceValueMention (id + "-1", extent, fileText);
		// generate value
		AceValue value = new AceValue (id, type, subtype);
		// add valueMention to value
		value.addMention (mention);
		// add value to aceDoc
		aceDoc.addValue (value);
	}

	/**
	 *  remove AceValues of type Crime, Sentence, and Job-Title which are
	 *  not referenced by an event.  This is done after event detection, since
	 *  values of these types are to be recorded only if reference by an event.
	 */

	public static void pruneAceValues (AceDocument aceDoc) {
		ArrayList<AceValue> values = aceDoc.values;
		HashSet<AceValue> referencedValues = new HashSet<AceValue>();
		for (AceEvent event : aceDoc.events) {
			for (AceEventMention evMention : event.mentions) {
				for (AceEventMentionArgument arg : evMention.arguments) {
					if (arg.value instanceof AceValueMention) {
						AceValue value = ((AceValueMention) arg.value).value;
						referencedValues.add(value);
					}
				}
			}
		}
		for (AceValue value : values) {
			String type = value.type;
			if (type.equals("Numeric") || type.equals("Contact-Info")) {
				referencedValues.add(value);
			}
		}
		values.retainAll(referencedValues);
		logger.info ("{} values remain after pruning.", aceDoc.values.size());
	}
}
