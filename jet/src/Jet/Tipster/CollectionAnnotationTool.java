// -*- tab-width: 4 -*-
package Jet.Tipster;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.util.*;

import Jet.Lisp.FeatureSet;
import Jet.Lex.Tokenizer;
import Jet.Zoner.SentenceSplitter;

/**
 *  a tool for displaying a collection and allowing the AnnotationTool to
 *  be invoked on documents in the collection.
 */

public class CollectionAnnotationTool extends JFrame {

	AnnotationTool tool;
	String[] types;

	public static void main(String[] args) {
		String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
		String collectionFile = home + "ACE 05/pre-pilot files.txt";
		String colorFile = "pilot event colors.txt";
		JFrame jf = new CollectionAnnotationTool(collectionFile, colorFile);
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

	/**
	 *  create a tool to annotated documents from collection 'collectionFile',
	 *  using the tag set (with colors and key bindings) specified by
	 *  'colorFile'.
	 */

	public CollectionAnnotationTool (String collectionFile, String colorFile) {
		// 1. open collection
		DocumentCollection col = new DocumentCollection(collectionFile);
		col.open();
		// 2. read in color file
		AnnotationColor ac = new AnnotationColor(null, colorFile);
		AnnotationColor.showColors();
		// 3. create frame displaying collection
		createCollectionFrame(col);
		// 4. initialize annotation tool
		tool = new AnnotationTool();
		// 5. add colors to annotation tool
		ArrayList colors = ac.colors;
		HashSet typeSet = new HashSet();
		for (int i=0; i<colors.size(); i++) {
			AnnotationColorEntry entry = (AnnotationColorEntry) colors.get(i);
			Annotation ann = new Annotation (entry.type, null,
			                                 new FeatureSet(entry.feature,
			                                                entry.featureValue));
			char key = entry.key;
			tool.addType (key, ann);
			typeSet.add(entry.type);
		}
		types = (String[]) typeSet.toArray(new String[0]);
	}

	private void createCollectionFrame (DocumentCollection col) {
		if (col == null)
			return;
		int size = col.size();
		Box mainBox = Box.createHorizontalBox();
		JPanel namePanel = new JPanel();
		namePanel.setLayout (new GridLayout (size,1));
		JPanel buttonPanel1 = new JPanel();
		buttonPanel1.setLayout (new GridLayout (size,1));
		mainBox.add(namePanel);
		mainBox.add(buttonPanel1);
		Border raised = new BevelBorder(BevelBorder.RAISED);
		for (int idoc = 0; idoc < size; idoc++) {
			ExternalDocument doc = col.get(idoc);
			String name = doc.fileName();
			JCheckBox checkBox = new JCheckBox(name);
			namePanel.add(checkBox);
			JButton annotateButton = new JButton ("annotate");
			annotateButton.setBorder(raised);
			annotateButton.addActionListener (new DocumentAnnotator (doc, name, checkBox));
			buttonPanel1.add(annotateButton);
		}
		JScrollPane scrollPane = new JScrollPane(mainBox);
		getContentPane().add(scrollPane);
		setSize(600, Math.min(400, size*30 + 60));
		setTitle ("Collection " + col.getName());
		setVisible(true);
	}

	/**
   *  stand-alone utility to annotate a collection, invoked by
   *  jet -AnnotateCollection [collection colorFile].  Passed an array with
   *  "-AnnotateCollection" followed optionally by the two file names.
   *  If the names are not specified, a file chooser is displayed to
   *  select the files.
   */

  public static void task (String[] args) {
  	String collectionFile = null, colorFile = null;
  	if (args.length == 3) {
  		collectionFile = args[1];
  		colorFile = args[2];
  	} else if (args.length == 1) {
  		JFileChooser chooser = new JFileChooser();
  		chooser.setDialogTitle("Collection to annotate");
  		int returnVal = chooser.showOpenDialog(null);
  		if(returnVal != JFileChooser.APPROVE_OPTION) System.exit(1);
  		collectionFile = chooser.getSelectedFile().getPath();
  		chooser.setDialogTitle("Color file");
  		returnVal = chooser.showOpenDialog(null);
  		if(returnVal != JFileChooser.APPROVE_OPTION) System.exit(1);
  		colorFile = chooser.getSelectedFile().getPath();
  	} else {
			System.out.println
			  ("AnnotateCollection requires 2 arguments: jet -AnnotateCollection <collection> <color file>");
			System.exit(1);
		}
		JFrame jf = new CollectionAnnotationTool(collectionFile, colorFile);
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

	private class DocumentAnnotator implements ActionListener {
		ExternalDocument doc;
		String documentName;
		JCheckBox checkBox;
		private DocumentAnnotator (ExternalDocument d, String dName, JCheckBox cb) {
			doc  = d;
			documentName = dName;
			checkBox = cb;
		}
		public void actionPerformed (ActionEvent ev) {
			Thread annotatorThread = new Thread() {
				public void run () {
					doc.setSGMLtags(types);
					doc.open();
					doc.annotateWithTag("TEXT");
					Vector textSegments = doc.annotationsOfType ("TEXT");
					if (textSegments == null || textSegments.size() == 0)
						return;
					Annotation text = (Annotation) textSegments.get(0);
					Span textSpan = text.span();
					SentenceSplitter.split (doc, textSpan);
					Vector sentences = doc.annotationsOfType ("sentence");
					if (sentences == null) return;
					Iterator is = sentences.iterator ();
					while (is.hasNext ()) {
						Annotation sentence = (Annotation)is.next ();
						Span sentenceSpan = sentence.span();
						Tokenizer.tokenize (doc, sentenceSpan);
					}
					tool.annotateDocument (doc, textSpan);
					checkBox.setSelected(true);
					doc.removeAnnotationsOfType("token");
					// don't wrap on write
					doc.setSGMLwrapMargin(0);
					doc.save();
				}
			};
			annotatorThread.start();
		}
	}
}
