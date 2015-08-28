// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Tipster;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.util.*;

import Jet.JetTest;
import Jet.Refres.EntityView;
import Jet.Refres.CorefFilter;

/**
 *  display of a DocumentCollection, with buttons to select views of
 *  individual Documents.  Either a standard view (for all annotations)
 *  or an entity view (for entity annotations) can be selected.
 */

public class CollectionView extends JFrame {

	/**
	 *  create a viewer for Collection 'col'.
	 */

	public CollectionView (DocumentCollection col) {
		this (col, false);
	}

	private CollectionView (DocumentCollection col, boolean createEntities) {
		if (col == null)
			return;
		int size = col.size();
		Box mainBox = Box.createHorizontalBox();
		JPanel namePanel = new JPanel();
		namePanel.setLayout (new GridLayout (size,1));
		JPanel buttonPanel1 = new JPanel();
		buttonPanel1.setLayout (new GridLayout (size,1));
		JPanel buttonPanel2 = new JPanel();
		buttonPanel2.setLayout (new GridLayout (size,1));
		mainBox.add(namePanel);
		mainBox.add(buttonPanel1);
		mainBox.add(buttonPanel2);
		Border raised = new BevelBorder(BevelBorder.RAISED);
		for (int idoc = 0; idoc < size; idoc++) {
			ExternalDocument doc = col.get(idoc);
			String name = doc.fileName();
			JLabel documentName = new JLabel(name);
			namePanel.add(documentName);
			JButton viewButton = new JButton ("view annotations");
			viewButton.setBorder(raised);
			viewButton.addActionListener (new ViewCreator (doc, name, false, createEntities));
			buttonPanel1.add(viewButton);
			boolean entitiesDiffer = doc.annotationsOfType("entitiesDiffer") != null;
			JButton viewEntities = new JButton
				("view entities" + (entitiesDiffer?"*":""));
			viewEntities.setBorder(raised);
			viewEntities.addActionListener (new ViewCreator (doc, name, true, createEntities));
			buttonPanel2.add(viewEntities);
		}
		JScrollPane scrollPane = new JScrollPane(mainBox);
		getContentPane().add(scrollPane);
		setSize(600, 400);
		setTitle ("Collection " + col.getName());
		setVisible(true);
	}

	public static void main(String[] args) {
		String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
		DocumentCollection col = new DocumentCollection(home + "ACE/training nwire coref.txt");
		col.open();
		new CollectionView(col, true);
	}

	private class ViewCreator implements ActionListener {
		ExternalDocument doc;
		String documentName;
		boolean showEntities;
		boolean createEntities;
		private ViewCreator (ExternalDocument d, String dName, boolean se, boolean ce) {
			doc  = d;
			showEntities = se;
			createEntities = ce;
		}
		public void actionPerformed (ActionEvent ev) {
			doc.setAllTags(true);
			doc.open();
			if (showEntities) {
				if (createEntities && doc.annotationsOfType("entity") == null)
					CorefFilter.buildEntitiesFromMentions(doc);
				new EntityView (doc, 0);
			} else {
				new View (doc, 0);
			}
		}
	}

	/**
   *  stand-alone utility to display a document collection, invoked by
   *  jet -CollectionView collection.  Passed an array with "-CollectionView" and
   *  the file name of the collection.  The collection name may be optionally
   *  preceded by '-encoding' and the name of a character set.
   */

  public static void task (String[] args) {
  	String encoding = null;
  	String file = null;
  	if (args.length == 4 && args[1].equals("-encoding")) {
  		encoding = args[2];
  		file = args[3];
  	} else if (args.length == 2) {
  		file = args[1];
  	} else {
			System.out.println
			  ("CollectionView requires 1 argument: jet -CollectionView [-encoding e] <collection>");
			System.exit(1);
		}
		// exit if invalid encoding is provided
		if (encoding != null) {
			if (!JetTest.setEncoding(encoding)) System.exit(1);
		}
		DocumentCollection col = new DocumentCollection(file);
		col.open();
		new CollectionView (col, true);
	}

}
