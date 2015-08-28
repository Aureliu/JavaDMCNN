// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2003, 2004, 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil
//              (ACE extensions)

package AceJet;

import java.util.*;
import java.io.*;

import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;

/**
 *  an Ace Entity, with information from the ACE key.  The 'id' field is inherited from its
 *  superclass, AceEventArgumentValue.
 */

public class AceEntity extends AceEventArgumentValue {

	/**
	 *  the EDT type of the entity:  PERSON, ORGANIZATION, GPE,
	 *  LOCATION, or FACILITY;  for ACE 2004, also WEAPON or VEHICLE.
	 */
	public String type;
	/**
	 *  the subtype of the EDT type;  null for pre-2004 annotation.
	 */
	public String subtype;
	/**
	 *  (for ACE 2004 and later) class of entity:  NEG|SPC|GEN|USP
	 **/
	public String entClass;
	/**
	 *  true if the entity is generic (class not SPC)
	 */
	public boolean generic;
	/**
	 *  a list of the mentions of the entity
	 */
	public ArrayList<AceEntityMention> mentions = new ArrayList<AceEntityMention>();
	/**
	 *  a list of the names of the entity
	 */
	public ArrayList<AceEntityName> names = new ArrayList<AceEntityName>();

	// map from APF type names to 'standard' names

	static HashMap standardType = new HashMap<String, String>();
	static {standardType.put("GSP", "GPE");
	        standardType.put("PER", "PERSON");
	        standardType.put("ORG", "ORGANIZATION");
	        standardType.put("LOC", "LOCATION");
	        standardType.put("FAC", "FACILITY");
	     }

	public AceEntity (String id, String type, String subtype, boolean generic) {
		this.id = id;
		this.type = type;
		this.subtype = subtype;
		this.generic = generic;
	}

	/**
	 *  create an AceEntity from the information in the APF file.
	 *  @param entityElement the XML element from the APF file containing
	 *                       information about this entity
	 *  @param fileText      the text of the document, excluding XML tags
	 */

	public AceEntity (Element entityElement, String fileText) {
			id = entityElement.getAttribute("ID");
			if (AceDocument.ace2004) {
				type = entityElement.getAttribute("TYPE");
				subtype = entityElement.getAttribute("SUBTYPE");
				entClass = entityElement.getAttribute("CLASS");
				generic = !entClass.equals("SPC");
			} else {
				NodeList entityTypeList = entityElement.getElementsByTagName("entity_type");
				Element entityType = (Element) entityTypeList.item(0);
				String genericString = entityType.getAttribute("GENERIC");
				generic = genericString.equals("TRUE");
				entClass = null;
				type = AceDocument.getElementText (entityElement, "entity_type");
			}
			if (standardType.containsKey(type))
					type = (String) standardType.get(type);
			// collect entity mentions
			NodeList mentionElements = entityElement.getElementsByTagName("entity_mention");
			for (int j=0; j<mentionElements.getLength(); j++) {
				Element mentionElement = (Element) mentionElements.item(j);
				AceEntityMention mention = new AceEntityMention (mentionElement, fileText);
				addMention(mention);
			}
			// sort mentions by end of head -- earlier mention first
			for (int i = 0; i < mentions.size()-1; i++) {
	      for (int j = i+1; j < mentions.size(); j++) {
	        AceEntityMention meni = mentions.get(i);
	        AceEntityMention menj = mentions.get(j);
	        if (meni.head.end() > menj.head.end()) {
	          mentions.set(i,menj);
	          mentions.set(j,meni);
	        }
	      }
	    }
	    // collect names
	    NodeList entityAttributesList = entityElement.getElementsByTagName("entity_attributes");
	    if (entityAttributesList.getLength() > 0) {
		    Element entityAttributes = (Element) entityAttributesList.item(0);
		    NodeList nameList = entityAttributes.getElementsByTagName("name");
		    for (int j=0; j<nameList.getLength(); j++) {
		    	Element nameElement = (Element) nameList.item(j);
		    	AceEntityName name = new AceEntityName (nameElement, fileText);
					addName(name);
		    }
		  }
	}

	/**
	 *  adds mention 'mention' to the mentions of this entity.
	 */

	public void addMention (AceEntityMention mention) {
		mentions.add(mention);
		mention.entity = this;
	}

	/**
	 *  adds name 'name' to the names associated with this entity.
	 */

	public void addName (AceEntityName name) {
		names.add(name);
	}

	public AceEntityMention findMention (String id) {
		for (int i=0; i<mentions.size(); i++) {
			AceEntityMention mention = mentions.get(i);
			if (mention.id.equals(id)) {
				return mention;
			}
		}
		return null;
	}

	/**
	 *  writes the entity to 'w' in APF format.
	 */

	public void write (PrintWriter w) {
		if (AceDocument.ace2004) {
			w.print ("  <entity ID=\"" + id + "\"");
			if (type != null && !type.equals(""))
				w.print (         " TYPE=\"" + type.substring(0,3) + "\"");
			if (subtype != null && !subtype.equals(""))
				w.print (       " SUBTYPE=\"" + subtype + "\"");
			if (entClass != null)
				w.println (       " CLASS=\"" + entClass + "\">");
			else
				w.println (       " CLASS=\"" + (generic?"GEN":"SPC") + "\">");
		} else {
		w.println ("  <entity ID=\"" + id + "\">");
		w.println ("    <entity_type GENERIC=\"" + (generic?"TRUE":"FALSE") +
		           "\">" + type + "</entity_type>");
		}
		for (int i=0; i<mentions.size(); i++) {
			AceEntityMention mention = mentions.get(i);
			mention.write(w);
		}
		w.println ("    <entity_attributes>");
		for (int i=0; i<names.size(); i++) {
			AceEntityName name = names.get(i);
			name.write(w);
		}
		w.println ("    </entity_attributes>");
		w.println ("  </entity>");
	}

}
