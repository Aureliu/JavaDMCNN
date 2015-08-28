// -*- tab-width: 4 -*-
package AceJet;

/**
 *   determines EDT class and genericity of words from training corpus.
 *   Main program writes EDT type dictionary and generic dictionary.
 *   <p>
 *   (July 04) New version supports training from both data with
 *   subtypes (2004 data) and data without subtypes (earlier data).
 *   Counts without subtypes are represented by a subtype "*".
 *   "total" subtype is total of all subtypes but does NOT include
 *   subtype "*".
 */

import java.io.*;
import java.util.*;

import Jet.Control;
import Jet.JetTest;
import Jet.Lex.EnglishLex;
import Jet.Parser.SynFun;
import Jet.Pat.Pat;
import Jet.Refres.Resolve;
import Jet.Tipster.*;
import Jet.Lisp.FeatureSet;

public class EDTtype {

    static ExternalDocument doc;
    static boolean monocase;
    static PrintStream writer, gwriter;
    static TreeMap<String, EDTtypeData> typeDataMap = new TreeMap<String, EDTtypeData>();
    static TreeSet<String> genericHeads = new TreeSet<String>();
    static int trainingMentions = 0, correct = 0, incorrect = 0, unknown = 0;
    static String apfFileSuffix, dataDir;
    static boolean useSubtype;

 	/*
      *  gather statistics from the files on file list (the text files and
 	 *  corresponding APF files) to gather statistics on EDT type as a 
 	 *  function of NP head, as well as statistics on generics (which were 
 	 *  needed until 2004).  Takes 4n+3 arguments:
 	 *  <ul>
 	 *  <li> property-file:   Jet properties file for extracting mentions
 	 *  <li> type-dict:       (output) dictionary with statistics on EDT type of each head
 	 *  <li> generic-dict:    (output) dictionary with statistics on generics
 	 *  <li> year:            2002/3/4/5 -- year when APF file was created
 	 *  <li> useSubtypes:     if '-', ignore subtype info from this APF file;
 	 *                        otherwise, use subtype info
 	 *  <li> directory:       directory containing  text and APF files
 	 *  <li> filelist:        list of documents (with text and APF files)
 	 *  <li> ... (the last four arguments may be repeated for multiple file lists)
 	 *  <ul>
 	 */

    public static void main(String[] args) throws IOException {
        if (args.length < 7 || ((args.length - 3) % 4) != 0) {
            System.err.println("EDTtype must have 4n+3 arguments:");
            System.err.print("  property-file type-dict generic-dict ");
            System.err.print("(year useSubtypes directory filelist)+");
            System.exit(1);
        }
        // initialize Jet
        System.out.println("Starting ACE EDT Type / Generic Training ...");
        JetTest.initializeFromConfig(args[0]);
        Pat.trace = false;
        Resolve.ACE = true;

        writer = new PrintStream(new FileOutputStream(args[1]));
        gwriter = new PrintStream(new FileOutputStream(args[2]));

        for (int iarg = 3; iarg < args.length; iarg += 4) {
            String year = args[iarg];
            useSubtype = !args[iarg + 1].equals("-");
            String directory = args[iarg + 2];
            if (!directory.endsWith("/")) directory += "/";
            String filelist = args[iarg + 3];
            if (year.equals("2002")) {
                AceDocument.ace2004 = false;
                AceDocument.ace2005 = false;
                apfFileSuffix = ".sgm.tmx.rdc.xml";
            } else if (year.equals("2003")) {
                AceDocument.ace2004 = false;
                AceDocument.ace2005 = false;
                apfFileSuffix = ".apf.xml";
            } else if (year.equals("2004")) {
                AceDocument.ace2004 = true;
                AceDocument.ace2005 = false;
                apfFileSuffix = ".apf.xml";
            } else if (year.equals("2005")) {
                AceDocument.ace2004 = true;
                AceDocument.ace2005 = true;
                apfFileSuffix = ".apf.xml";
            } else {
                System.err.println("Invalid year " + year + " in argument list.");
                System.err.println("(Only 2002 - 2005 allowed.)");
                System.exit(1);
            }
            trainFromFileList(directory, filelist);
        }
        writeTypeDict(writer);
        writeGenericDict(gwriter);
        EDTtypeData.reportSubtypeTotals();
        System.out.println(trainingMentions + " training mentions");
        System.out.println(correct + " correct predictions, " + incorrect + " incorrect");
        System.out.println(unknown + " unknown");
    }

    /**
     * train the EDT type classifier from the set of documents on file 'fileList'
     * (one document per line).
     */

    static void trainFromFileList(String dataDir, String fileList) throws IOException {
        String currentDoc;
        // open list of files
        BufferedReader reader = new BufferedReader(new FileReader(fileList));
        int docCount = 0;
        while ((currentDoc = reader.readLine()) != null) {
            // if (true) continue;
            // process file 'currentDoc'
            docCount++;
            System.out.println("\nProcessing document " + docCount + ": " + currentDoc);
            // read document
            String textFileName = dataDir + currentDoc + ".sgm";
            doc = new ExternalDocument("sgml", textFileName);
            doc.setAllTags(true);
            if (!AceDocument.ace2005)
                doc.setEmptyTags(new String[]{"W", "TURN"});
            doc.open();
            // check document case
            Ace.monocase = Ace.allLowerCase(doc);
            // process document
            Control.processDocument(doc, null, false, docCount);
            // read key file with mention information
            // (populate mentionSet and mentionStartMap)
            String apfFileName = dataDir + currentDoc + apfFileSuffix;
            AceDocument aceDoc = new AceDocument(textFileName, apfFileName);
            LearnRelations.findEntityMentions(aceDoc);
            // process possible mentions
            processMentions(doc);
        }
    }

    static void processMentions(ExternalDocument doc) {
        // gather all mentions in document using Resolve.gatherMentions
        Vector mentions = Resolve.gatherMentions(doc, new Span(0, doc.length()));
        // for each mention in text
        for (int imention = 0; imention < mentions.size(); imention++) {
            Annotation mention = (Annotation) mentions.get(imention);
            Annotation head = Resolve.getHeadC(mention);
            String cat = (String) head.get("cat");
            if (cat.equals("pro") || cat.equals("det") || cat.equals("name"))
                continue;
            String headString = Resolve.normalizeName(doc.text(head));
            if (monocase)
                headString = headString.toLowerCase();
            // is there a corresponding mention in APF key?
            AceEntityMention apfMention =
                    (AceEntityMention) LearnRelations.mentionStartMap.get(new Integer(head.start()));
            //    if so, classify, else "other"
            String EDTtype = "OTHER";
            String EDTsubtype = "";
            if (apfMention != null) {
                EDTtype = apfMention.entity.type;
                EDTsubtype = apfMention.entity.subtype;
            }
            //    use subtype info only from latest docs;  otherwise use subtype '*'
            if (!useSubtype)
                EDTsubtype = "*";
            boolean training = trainingMentions < 200000; // true;
            if (training) {
                trainingMentions++;
                // retrieve / create WordType record
                EDTtypeData wt = typeDataMap.get(headString);
                if (wt == null) {
                    wt = new EDTtypeData(headString);
                    typeDataMap.put(headString, wt);
                }
                wt.incrementTypeCount(EDTtype, EDTsubtype, 1);
                if (apfMention != null)
                    wt.incrementGenericCount(apfMention.entity.generic);
            } else {
                String prediction = bareType(getTypeSubtype(doc, null, mention));
                if (prediction.equals(EDTtype)) {
                    correct++;
                } else {
                    incorrect++;
                    System.out.print("Word: " + headString);
                    System.out.println(" predict " + prediction + ", should be " + EDTtype);
                }
            }
        }
    }

    /**
     * writes the EDT type dictionary to <CODE>writer</CODE>.
     */

    static void writeTypeDict(PrintStream writer) {
        Iterator it = typeDataMap.values().iterator();
        while (it.hasNext()) {
            ((EDTtypeData) it.next()).write(writer);
        }
        writer.close();
    }

    /**
     * reads the EDT type dictionary from the file specified by Jet
     * parameters <CODE>Ace.EDTtype.fileName</CODE> and
     * <CODE>Ace.EDTtype.auxFileName</CODE>.
     */

    public static void readTypeDict() {
        typeDataMap = new TreeMap<String, EDTtypeData>();
        String fileName = JetTest.getConfigFile("Ace.EDTtype.fileName");
        if (fileName != null) {
            readTypeDict(fileName);
        } else {
            System.err.println("EDTtype.readTypeDict:  no file name specified in config file");
        }
        String auxFileName = JetTest.getConfigFile("Ace.EDTtype.auxFileName");
        if (auxFileName != null) {
            File f = new File(auxFileName);
            if (f.exists() && !f.isDirectory()) {
                readTypeDict(auxFileName);
            }
        } 
    }

    /**
     * reads the EDT type dictionary from file <CODE>dictFile</CODE>.
     */

    public static void readTypeDict(String dictFile) {
        System.err.println("Loading type dictionary " + dictFile);
        try {
            BufferedReader reader = new BufferedReader(new FileReader(dictFile));
            String line;
            while ((line = reader.readLine()) != null) {
                EDTtypeData data = EDTtypeData.readLine(line);
                if (data != null)
                    typeDataMap.put(data.word, data);
            }
            System.err.println("Type dictionary loaded.");
        } catch (IOException e) {
            System.err.print("Unable to load dictionary due to exception: ");
            System.err.println(e);
        }
    }

    /**
     * returns <CODE>true</CODE> if an EDT type dictionary has been loaded.
     */

    public static boolean isDictLoaded() {
        return !typeDataMap.isEmpty();
    }

    static void writeGenericDict(PrintStream writer) {
        Iterator it = typeDataMap.values().iterator();
        while (it.hasNext()) {
            EDTtypeData td = (EDTtypeData) it.next();
            if (td.genericCount > 0 || td.nonGenericCount > 0)
                writer.println(td.word + " | " + td.genericCount +
                        " " + td.nonGenericCount);
        }
        writer.close();
    }

    public static void emptyGenericDict() {
        genericHeads = new TreeSet<String>();
    }

    public static void readGenericDict() {
        String fileName = JetTest.getConfigFile("Ace.generic.fileName");
        if (fileName != null) {
            readGenericDict(fileName);
        } else {
            System.err.println("EDTtype.readGenericDict:  no file name specified in config file");
        }
    }

    public static void readGenericDict(String dictFile) {
        System.err.println("Loading generic dictionary.");
        genericHeads = new TreeSet<String>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(dictFile));
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.indexOf('|');
                if (split <= 1) {
                    System.err.println("** error in generic dict: " + line);
                    return;
                }
                String term = line.substring(0, split - 1);
                String typeStatistics = line.substring(split + 2);
                StringTokenizer st = new StringTokenizer(typeStatistics);
                String genericCountString = st.nextToken();
                String nonGenericCountString = st.nextToken();
                int genericCount = Integer.valueOf(genericCountString).intValue();
                int nonGenericCount = Integer.valueOf(nonGenericCountString).intValue();
                if (genericCount > nonGenericCount && (genericCount + nonGenericCount) > 2)
                    genericHeads.add(term);
            }
            System.err.println("Generic dictionary loaded.");
        } catch (IOException e) {
            System.err.print("Unable to load dictionary due to exception: ");
            System.err.println(e);
        }
    }

    public static boolean hasGenericHead(Document doc, Annotation mention) {
        Annotation headC = Resolve.getHeadC(mention);
        String headWord = Resolve.normalizeName(doc.text(headC).trim());
        return genericHeads.contains(headWord);
    }

    private static final String[] partitives = {"group", "part", "member", "portion",
            "center", "bunch", "couple", "remainder", "rest", "lot", "percent", "%",
            "dozen", "hundred", "thousand", /* also, 'a number of' but not 'the number of' */
            "some", "either", "neither", "any", "each", "all", "both", "none",
            "most", "many", "afew", "one", "q"};

    private static final String[] governmentTitles = {"Vice-President",
            "Vice-Premier", "Prime-Minister", "Foreign-Minister",
            "Foreign-Secretary", "Secretary-of-State", "Attorney-General",
            "Justice-Minister", "Secretary-General"};

    /**
     * returns the EDT type of a mention:  PERSON, GPE, ORGANIZATION,
     * LOCATION, FACILITY, or OTHER (where OTHER indicates that it is not
     * and EDT mention).
     */

    public static String getTypeSubtype(Document doc, Annotation entity,
                                        Annotation mention) {
        String paHead = SynFun.getHead(doc, mention).toLowerCase();
        String det = SynFun.getDet(mention);
        boolean isHumanMention = SynFun.getHuman(mention);
        Annotation headC = Resolve.getHeadC(mention);
        // for perfect mentions
        if (Ace.perfectMentions) {
            String tsubt = PerfectAce.getTypeSubtype(headC);
            if (tsubt != null && !tsubt.equals(""))
                return tsubt;
            else
                System.err.println("*** no type info for " + doc.text(headC));
        }
        // look up in gazetteer
        String gazetteerType = getGazetteerTypeSubtype(doc, mention);
        if (gazetteerType != null)
            return gazetteerType;
        String headWord = Resolve.normalizeName(doc.text(headC).trim());
        String name = SynFun.getName(doc, mention);
        String cat = (String) headC.get("cat");
        if (cat == null) {
            cat = "nn";
        }
        // for named mentions, use type assigned by name tagger
        if (name != null) {
            if (paHead == null ||
                paHead.equalsIgnoreCase("otherName") ||
                paHead.equals("url"))
                return "OTHER";
            // email address => PERSON
            if (paHead.equals("email"))
                return (AceDocument.ace2005 ?
                        typeAndSubtype("PERSON", "Individual") : "PERSON");
            String type = paHead.toUpperCase().intern();
            // part of GPE => LOCATION (e.g., southern Switzerland)
            if (sectionOfGPE(doc, mention, type, headC))
                return typeAndSubtype("LOCATION",
                        (AceDocument.ace2005 ? "Region-General" : "Region-National"));
	    String subtype = null;
	    Object pa = SynFun.getPA(mention);
	    if (pa != null && pa instanceof FeatureSet)
		// name from onomasticon may have subtype
		subtype = (String) ((FeatureSet) pa).get("subtype");
            if (subtype == null)
                // use separate model to assign subtype for names
                subtype = NameSubtyper.classify(name, type);
            return typeAndSubtype(type, subtype);
        }
        // for phrases such as "group of X", "part of X", "all of X",
        // use the type of phrase X.
        if (in(paHead, partitives) || headC.get("cat") == "q") {
            // 'of' complement may be at lower level of tree, so go down tree
            // until we find such a complement
            Annotation x = mention;
            while (x != null && x.get("of") == null)
                x = (Annotation) x.get("headC");
            if (x != null) {
                Annotation of = (Annotation) x.get("of");
                if (Ace.entityTrace)
                    System.out.println("Using computed type for " + paHead);
                String type = getTypeSubtype(doc, null, of);
                // special case:  parts of a GPE are a LOCATION
                if (bareType(type).equals("GPE") &&
                        (paHead.equals("part") || paHead.equals("portion")))
                    type = AceDocument.ace2005 ? "LOCATION:Region-General" :
                            "LOCATION:Region-Subnational";
                // Ace.partitiveMap.put(new Integer(mention.span().start()), new Integer(of.span().start()));
                return type;
            }
        }
        // for pronouns, not in partitives, return "OTHER"
        // (this suppresses entities whose first mention is a pronoun)
        if (cat.equals("pro") || cat.equals("det") || cat.equals("q"))
            return "OTHER";
        // for some nouns, EDTtype depends on whether they appear with a
        // determiner;  handle these separately
        String type = handCodedEDTtype(det, headWord);
        if (type != null)
            return type;
        // for all other nouns, look head up in EDT type dictionary
        //    first use actual (inflected) head
        type = lookUpEDTtype(headWord.toLowerCase());
        if (type != null)
            return type.intern();
        //    then try with regularized head from PA structure (singular form of plural word)
        type = lookUpEDTtype(paHead);
        if (type != null)
            return type.replaceAll("Individual", "Group").intern();
        // if there is no entry for singular form, check if plural form has entry
        String[] singular = new String[1];
        singular[0] = paHead;
        String[] plural = EnglishLex.nounPlural(singular);
        type = lookUpEDTtype(plural[0]);
        if (type != null)
            return type.replaceAll("Group", "Individual").intern();
        // if no entries at all, and entity has feature 'human' from Comlex,
        // treat as a person
        if (Ace.preferRelations) {
            if (isHumanMention || (entity != null && entity.get("human") == "t"))
                return "PERSON:Individual";
        }
        unknown++;
        return "OTHER";
    }

    private static String getGazetteerTypeSubtype(Document doc, Annotation mention) {
        if (Ace.gazetteer == null)
            return null;
        String[] headTokens = Resolve.getHeadTokens(doc, mention);
        if (mention.get("cat") == "np") {
            // if (Resolve.isGenericNationals(doc, mention))
            // 	return "GPE:Nation";
            // else
            if (Ace.gazetteer.isNational(headTokens))
                return AceDocument.ace2005 ? "PERSON:Individual" : "PERSON";
            else if (Ace.gazetteer.isNationals(headTokens))
                return AceDocument.ace2005 ? "PERSON:Group" : "PERSON";
        } else {
            if (Ace.gazetteer.isNationality(headTokens))
                return "GPE:Nation";
        }
        return null;
    }

    /**
     * determines whether named 'mention', with head 'headC', of EDRtype
     * 'type', is a reference to a section of a GPE.  Such mentions are
     * classfied as LOCATION:Region-General in ACE.  If it is, the method
     * returns <CODE>true</CODE> and also sets the feature
     * <B>nameWithModifier</B> on the mention.
     */

    private static boolean sectionOfGPE(Document doc, Annotation mention,
                                        String type, Annotation headC) {
        if (type != "GPE")
            return false;
        int startOfExtent = mention.start();
        int startOfHead = headC.start();
        if (startOfExtent == startOfHead)
            return false;
        String modifier = doc.text(new Span(startOfExtent, startOfHead));
        if (modifier.contains("north") || modifier.contains("south") ||
                modifier.contains("east") || modifier.contains("west") ||
                modifier.contains("central")) {
            mention.put("nameWithModifier", "t");
            // System.out.println ("Found sectionOfGPE " + doc.text(mention));
            return true;
        } else {
            return false;
        }
    }

    static HashMap<String, String> specifiedEDTtype = new HashMap<String, String>();

    static { // the following nouns are markable if they appear with
        // a determiner
        // (cf. "by force" and "by the American force")
        specifiedEDTtype.put("force", "ORGANIZATION:Government");
        // ("on board" vs. "on the board")
        specifiedEDTtype.put("board", "ORGANIZATION:Commercial");
        // ("in prison" vs. "in the prison")
        specifiedEDTtype.put("prison", "FACILITY:" +
                (AceDocument.ace2005 ? "Building-Grounds" : "Building"));
        specifiedEDTtype.put("room", "FACILITY:" +
                (AceDocument.ace2005 ? "Subarea-Facility" : "Subarea-Building"));
        specifiedEDTtype.put("home", "FACILITY:" +
                (AceDocument.ace2005 ? "Building-Grounds" : "Building"));
        specifiedEDTtype.put("state", "GPE:State-or-Province");
        specifiedEDTtype.put("land", "LOCATION:" +
                (AceDocument.ace2005 ? "Region-General" : "Region-Subnational"));
        // 'minister' as a noun is markable
        // 'Minister' as part of a title is not
        specifiedEDTtype.put("minister", "PERSON" +
                (AceDocument.ace2005 ? ":Individual" : ""));
    }

    static String handCodedEDTtype(String determiner, String head) {
        String type = (String) specifiedEDTtype.get(head);
        if (type == null) return null;
        if (determiner == null) return "OTHER";
        return type;
    }

    static String lookUpEDTtype(String word) {
        if (word == null) return null;
        EDTtypeData data = (EDTtypeData) typeDataMap.get(word.toLowerCase());
        if (data == null) return null;
        return data.getBestTypeSubtype();
    }

    private static boolean in(Object o, Object[] array) {
        for (int i = 0; i < array.length; i++)
            if (array[i] != null && array[i].equals(o)) return true;
        return false;
    }

    public static String bareType(String typeSubtype) {
        int p = typeSubtype.indexOf(':');
        if (p > 0)
            return typeSubtype.substring(0, p);
        else
            return typeSubtype;
    }

    static String subtype(String typeSubtype) {
        int p = typeSubtype.indexOf(':');
        if (p > 0)
            return typeSubtype.substring(p + 1);
        else
            return "";
    }

    static String typeAndSubtype(String type, String subtype) {
        return type.toUpperCase() + ":" + subtype;
    }

}

/**
 * information about a word:  how frequently is appears as each EDT type, and
 * how frequently it appears as generic or non-generic.
 */

class EDTtypeData {

    // a two-level hash map, of the form type -> (subtype -> count)
    // giving the totals over all words of a given type
    static HashMap subtypeTotals = new HashMap();

    String word;
    String type = null;     // most frequent type
    String subtype = null;  // most frequent subtype
    int count = 0;
    // a two-level hash map, of the form  type -> (subtype -> count)
    //   the total for a type is          type -> ("total" -> count)
    //   the count with no subtype is     type -> ("*"     -> count)
    HashMap typeCount;
    int genericCount = 0, nonGenericCount = 0;

    EDTtypeData(String word) {
        this.word = word;
        typeCount = new HashMap();
    }

    /**
     * create EDTtypeData from an entry in the type dictionary, which
     * has the form
     * word | type count type count ...
     */

    static EDTtypeData readLine(String line) {
        int split = line.indexOf('|');
        if (split <= 1) {
            System.err.println("** error in ACE type dict: " + line);
            return null;
        }
        String term = line.substring(0, split - 1);
        EDTtypeData data = new EDTtypeData(term);
        String typeStatistics = line.substring(split + 2);
        StringTokenizer st = new StringTokenizer(typeStatistics);
        while (st.hasMoreTokens()) {
            String typeSubtype = st.nextToken();
            String countString = st.nextToken();
            int count = Integer.valueOf(countString).intValue();
            int p = typeSubtype.indexOf(':');
            String type, subtype;
            if (p > 0) {
                type = typeSubtype.substring(0, p);
                subtype = typeSubtype.substring(p + 1);
            } else {
                type = typeSubtype;
                subtype = "*";
            }
            data.incrementTypeCount(type, subtype, count);
        }
        return data;
    }

    String getBestType() {
        if (type == null) determineBestType();
        return type;
    }

    String getBestSubtype() {
        if (type == null) determineBestType();
        return subtype;
    }

    String getBestTypeSubtype() {
        return EDTtype.typeAndSubtype(getBestType(), getBestSubtype());
    }

    void write(PrintStream writer) {
        writer.print(word + " |");
        Iterator it = typeCount.keySet().iterator();
        while (it.hasNext()) {
            String type = (String) it.next();
            HashMap subMap = (HashMap) typeCount.get(type);
            Iterator it2 = subMap.keySet().iterator();
            while (it2.hasNext()) {
                String subtype = (String) it2.next();
                if (subtype == "total") continue;
                Integer cc = (Integer) subMap.get(subtype);
                if (subtype.equals("*")) {
                    writer.print(" " + type + " " + cc.intValue());
                } else {
                    writer.print(" " + type + ":" + subtype + " " + cc.intValue());
                }
            }
        }
        writer.println();
    }

    void incrementTypeCount(String type, String subtype, int incr) {
        incrementCount(typeCount, type, subtype, incr);
        if (!subtype.equals("*")) {
            incrementCount(typeCount, type, "total", incr);
            incrementCount(subtypeTotals, type, subtype, incr);
        }
    }

    /**
     * increments a counter in a two-level hash map
     */

    static void incrementCount(HashMap map, String a, String b, int incr) {
        HashMap aMap = (HashMap) map.get(a);
        if (aMap == null) {
            aMap = new HashMap();
            map.put(a, aMap);
        }
        Integer cc = (Integer) aMap.get(b);
        int c = (cc == null) ? 0 : cc.intValue();
        c += incr;
        aMap.put(b, new Integer(c));
    }

    void incrementGenericCount(boolean generic) {
        if (generic) {
            genericCount++;
        } else {
            nonGenericCount++;
        }
    }

    /**
     * determines the best type and subtype for a word.
     */

    void determineBestType() {
        if (type != null) return;
        int bestCount = 0;
        Iterator it = typeCount.keySet().iterator();
        while (it.hasNext()) {
            String tp = (String) it.next();
            Integer cc = (Integer) ((HashMap) typeCount.get(tp)).get("total");
            int c = (cc == null) ? 0 : cc.intValue();
            if (c > bestCount) {
                type = tp;
                bestCount = c;
            }
        }
        if (type == null) {
            // if there is no new (2004) data, use counts from old data
            it = typeCount.keySet().iterator();
            while (it.hasNext()) {
                String tp = (String) it.next();
                Integer cc = (Integer) ((HashMap) typeCount.get(tp)).get("*");
                int c = (cc == null) ? 0 : cc.intValue();
                if (c > bestCount) {
                    type = tp;
                    bestCount = c;
                }
            }
            if (type == null) {
                System.err.println("EDTtypeData.determineBestType failed for " + word);
            } else {
                subtype = bestSubtype(type);
            }
            return;
        }
        bestCount = -1;
        HashMap subtypeCount = (HashMap) typeCount.get(type);
        it = subtypeCount.keySet().iterator();
        while (it.hasNext()) {
            String subtp = (String) it.next();
            if (subtp == "total") continue;
            if (subtp.equals("*")) continue;
            Integer cc = (Integer) subtypeCount.get(subtp);
            int c = (cc == null) ? 0 : cc.intValue();
            if (c > bestCount) {
                subtype = subtp;
                bestCount = c;
            }
        }
    }

    static void reportSubtypeTotals() {
        Iterator it = subtypeTotals.keySet().iterator();
        while (it.hasNext()) {
            String type = (String) it.next();
            System.out.println("For type: " + type);
            HashMap subMap = (HashMap) subtypeTotals.get(type);
            Iterator it2 = subMap.keySet().iterator();
            while (it2.hasNext()) {
                String subtype = (String) it2.next();
                Integer cc = (Integer) subMap.get(subtype);
                System.out.print(subtype + " " + cc.intValue() + " ");
            }
            System.out.println();
        }
    }

    /**
     * for words which appear only in old training data and have no subtype,
     * returns the most common subtype for a given type.
     */

    static String bestSubtype(String type) {
        HashMap subMap = (HashMap) subtypeTotals.get(type);
        String best = null;
        int bestCount = 0;
        Iterator it2 = subMap.keySet().iterator();
        while (it2.hasNext()) {
            String subtype = (String) it2.next();
            Integer cc = (Integer) subMap.get(subtype);
            int c = cc.intValue();
            if (c > bestCount) {
                best = subtype;
                bestCount = c;
            }
        }
        if (best != null) {
            return best;
        } else if (type.equals("OTHER")) {
            return "";
        } else {
            System.err.println("*** Cannot determine best subtype for type " + type);
            return "Other";
        }
    }

}
