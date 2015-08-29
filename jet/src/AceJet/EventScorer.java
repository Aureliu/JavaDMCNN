// -*- tab-width: 4 -*-
package AceJet;

//Author:       Ralph Grishman
//Date:         September 2012

import java.util.*;
import java.io.*;
import Jet.Tipster.*;

/**
 *  EventScorer implements Ji's 3 event metrics, as defined in 
 *  Refining Event Extraction [Ji and Grishman, ACL 2008].  The
 *  three metrics are trigger labeling, argument identification,
 *  and argument role labeling.  These metrics are applied at the
 *  <i>event mention</i> level (not the event level).
 */

public class EventScorer {

	static String textDirectory;
	static String textExtension;
	static String systemApfDirectory;
	static String systemApfExtension;
	static String keyApfDirectory;
	static String keyApfExtension;

	/**
	 *  computes the average event scores for a set of documents.  Takes 
	 *  7 command-line arguments: <br>
	 *  docList:  file containing list of document file names, one per line <br>
	 *  textDirectory:  directory containing text files for documents <br>
	 *  textExtension:  file extension for text files <br>
	 *  systemApfDirectory:  directory containing APF files to be scored <br>
	 *  systemApfExtension:  file extension for APF files to be scored <br>
	 *  keyApfDirectory:  directory containing APF keys <br>
	 *  keyApfExtension:  file extension for APF keys <br>
	 *  If one line in the docList file is <i>d</i>, the program will
	 *  compare the APF files 
	 *  systemApfDirectory/<i>d</i>.systemApfExtension and
	 *  keyApfDirectory/<i>d</i>.keyApfExtension.
	 */

	public static void main (String[] args) throws IOException {

		if (args.length != 7) {
			System.out.println ("EventScorer requires 7 args:  ");
			System.out.print   ("    docList textDirectory textExtension ");
			System.out.println ("systemApfDirectory systemApfExtension keyApfDirectory keyApfExtension");
			System.exit(1);
		}

		String docListFile = args[0];
		textDirectory = args[1];
		textExtension = args[2];
		systemApfDirectory = args[3];
		systemApfExtension = args[4];
		keyApfDirectory = args[5];
		keyApfExtension = args[6];

		BufferedReader docListReader = new BufferedReader(new FileReader (docListFile));
		String docName;
//		while ((docName = docListReader.readLine()) != null)
//			scoreDocument (docName);		
		while ((docName = docListReader.readLine()) != null)
		{
			docName = getFileNameNoEx(docName);	//remove the extension of the file
			scoreDocument (docName);
		}

		computeScores ();
		reportScores ();
	}

	static int correctTriggers = 0;
	static int spuriousTriggers = 0;
	static int missingTriggers = 0;

	static float triggerRecall, triggerPrecision, triggerF;

	static int correctArguments = 0;
	static int spuriousArguments = 0;
	static int missingArguments = 0;

	static float argumentRecall, argumentPrecision, argumentF;

	static int correctRoles = 0;
	static int spuriousRoles = 0;
	static int missingRoles = 0;

	static float roleRecall, rolePrecision, roleF;

	public static void scoreDocument (String docName) {

		String textFileName = textDirectory + "/" + docName + "." + textExtension;
		ExternalDocument doc = new ExternalDocument("sgml", textFileName);	//creates a new external document associated with file 'fileName'.  The format of the file is given by 'format'.
		doc.setAllTags(true);
		doc.open();

		Set<String> systemTriggers = new HashSet<String>();
		Set<String> systemArguments = new HashSet<String>();
		Set<String> systemRoles = new HashSet<String>();
		String systemApfFileName = systemApfDirectory + "/" + docName + "." + systemApfExtension;
		readApf (textFileName, systemApfFileName, systemTriggers, systemArguments, systemRoles);

		Set<String> keyTriggers = new HashSet<String>();
		Set<String> keyArguments = new HashSet<String>();
		Set<String> keyRoles = new HashSet<String>();
		String keyApfFileName = keyApfDirectory + "/" + docName + "." + keyApfExtension;
		readApf (textFileName, keyApfFileName, keyTriggers, keyArguments, keyRoles);

		int docCorrectTriggers = sizeOfSetIntersection(systemTriggers, keyTriggers);	//same set is recorded.
		int docSpuriousTriggers = sizeOfSetDifference(systemTriggers, keyTriggers);
		int docMissingTriggers = sizeOfSetDifference(keyTriggers, systemTriggers);

		int docCorrectArguments = sizeOfSetIntersection(systemArguments, keyArguments);
		int docSpuriousArguments = sizeOfSetDifference(systemArguments, keyArguments);
		int docMissingArguments = sizeOfSetDifference(keyArguments, systemArguments);

		int docCorrectRoles = sizeOfSetIntersection(systemRoles, keyRoles);
		int docSpuriousRoles = sizeOfSetDifference(systemRoles, keyRoles);
		int docMissingRoles = sizeOfSetDifference(keyRoles, systemRoles);

		correctTriggers += docCorrectTriggers;
		spuriousTriggers += docSpuriousTriggers;
		missingTriggers += docMissingTriggers;

		correctArguments += docCorrectArguments;
		spuriousArguments += docSpuriousArguments;
		missingArguments += docMissingArguments;

		correctRoles += docCorrectRoles;
		spuriousRoles += docSpuriousRoles;
		missingRoles += docMissingRoles;
	}

	static void readApf (String textFileName, String apfFileName, 
			Set<String> triggers, Set<String> arguments, Set<String> roles) {
        AceDocument aceDoc = new AceDocument(textFileName, apfFileName);	//very important.creat structure data of text based on text and annotation,
		for (AceEvent event : aceDoc.events) {	// traverse event in aceDoc. just event, may include multiple event mention
			String eType = event.type + ":" + event.subtype; //record type and subtype e.g, life.die:
			for (AceEventMention mention : event.mentions) {	//traverse all event mention in event
				Span triggerSpan = mention.anchorJetExtent;	//e.g., doc - null , end - 212 , start - 205
				triggers.add(eType + ":" + triggerSpan);
				for (AceEventMentionArgument argument : mention.arguments) {
					AceMention arg = argument.value;
// doesn't really get head
					arguments.add(eType + ":" + arg.getJetHead());	//return jetHead
					roles.add(eType + ":" + argument.role + ":" + arg.getJetHead());
				}
			}
		} 
	}

	static void computeScores () {
		triggerRecall = ((float) correctTriggers) / (correctTriggers + missingTriggers);
		triggerPrecision = ((float) correctTriggers) / (correctTriggers + spuriousTriggers);
		triggerF = 2.0f * triggerPrecision * triggerRecall / (triggerPrecision + triggerRecall);

		argumentRecall = ((float) correctArguments) / (correctArguments + missingArguments);
		argumentPrecision = ((float) correctArguments) / (correctArguments + spuriousArguments);
		argumentF = 2.0f * argumentPrecision * argumentRecall / (argumentPrecision + argumentRecall);

		roleRecall = ((float) correctRoles) / (correctRoles + missingRoles);
		rolePrecision = ((float) correctRoles) / (correctRoles + spuriousRoles);
		roleF = 2.0f * rolePrecision * roleRecall / (rolePrecision + roleRecall);
	}

	static int sizeOfSetIntersection (Set a, Set b) {
		Set intersection = new HashSet (a);
		intersection.retainAll(b);
		return intersection.size();
	}

	static int sizeOfSetDifference (Set a, Set b) {
		Set difference = new HashSet (a);
		difference.removeAll(b);
		return difference.size();
	}

	static void reportScores () {
		System.out.printf ("Triggers:   R = %6.2f   P = %6.2f   F = %6.2f\n",
				   triggerRecall * 100, triggerPrecision * 100, triggerF * 100);
		System.out.printf ("Arguments:  R = %6.2f   P = %6.2f   F = %6.2f\n",
				   argumentRecall * 100, argumentPrecision * 100, argumentF * 100);
		System.out.printf ("Roles:      R = %6.2f   P = %6.2f   F = %6.2f\n",
				   roleRecall * 100, rolePrecision * 100, roleF * 100);
	}
	
    public static String getFileNameNoEx(String filename) {   
        if ((filename != null) && (filename.length() > 0)) {   
            int dot = filename.lastIndexOf('.');   
            if ((dot >-1) && (dot < (filename.length()))) {   
                return filename.substring(0, dot);   
            }   
        }   
        return filename;   
    } 

}
