// -*- tab-width: 4 -*-
package Jet.HMM;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;

import Jet.Tipster.*;
import Jet.Lex.*; // << for tokenizer

public class ActiveLearnerTool extends JFrame {

	public ActiveLearnerTool () {

		Container content = this.getContentPane();
		content.setLayout (new FlowLayout());
		this.setTitle("ActiveLearnerTool");

		JPanel panel = new JPanel();
		final JButton init = new JButton("init");
		panel.add(init);
		final JButton learn = new JButton("learn");
		panel.add(learn);
		learn.setEnabled(false);
		final JButton save = new JButton("save");
		panel.add(save);
		save.setEnabled(false);
		content.add(panel);
		pack();
		setVisible(true);

		String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
		new AnnotationColor(home + "HMM");
		ActiveLearner.col = new DocumentCollection(home + "HMM/NE/ACE training Collection.txt");

		init.addActionListener (new ActionListener() {
			public void actionPerformed (ActionEvent ev) {
				init.setEnabled(false);
				Thread initializerThread = new Thread() {
					public void run () {
						ActiveLearner.initialize();
						learn.setEnabled(true);
					}
				};
				initializerThread.start();
			}
		});

		learn.addActionListener (new ActionListener() {
			public void actionPerformed (ActionEvent ev) {
				learn.setEnabled(false);
				ActiveLearner.keepLearning = true;
				Thread learnerThread = new Thread() {
					public void run () {
						while (ActiveLearner.keepLearning)
							ActiveLearner.learn();
						learn.setEnabled(true);
						save.setEnabled(true);
					}
				};
				learnerThread.start();
			}
		});

		save.addActionListener (new ActionListener() {
			public void actionPerformed (ActionEvent ev) {
				System.out.println ("pushed save");
				// ActiveLearner.col.saveAs("...");
			}
		});
	}

	public static void main (String[] args) {
		new ActiveLearnerTool();
		/*
		// small test of Collection.saveAs
		String home = "C:/Documents and Settings/Ralph Grishman/My Documents/";
		DocumentCollection col = new DocumentCollection(home + "dir1/small NE train Collection.txt");
		col.open();
		for (int i=0; i<col.size(); i++) {
			ExternalDocument doc = col.get(i);
			System.out.println ("Reading " + doc.fileName());
			doc.setAllTags(true);
			doc.open();
		}
		col.saveAs(home + "dir2/collection.txt");
		*/
	}
}
