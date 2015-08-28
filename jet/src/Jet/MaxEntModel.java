// -*- tab-width: 4 -*-
package Jet;

import java.io.*;

import opennlp.maxent.*;
import opennlp.maxent.io.*;
import opennlp.model.*;
import AceJet.Datum;

/**
 * a wrapper for the maximum entropy code provided in the OpenNLP package.
 * modified by Yifan He in 2014 to optionally call Mallet max ent.
 */

public class MaxEntModel {

    String featureFileName;
    String modelFileName;
    PrintStream featureWriter = null;
    GISModel model = null;
    /**
     *  if true, create model with L2 regularization using Mallet;
     *  if false, use OpenNLP to create model (no regularization)
     */
    boolean USE_L2 = true;
    int cutoff = 4;
    int iterations = 100;

    /**
     * creates a new maximum entropy model.
     */

    public MaxEntModel() {
    }

    /**
     * creates a new maximum entropy model, specifying files for both
     * the features and the resulting model.
     *
     * @param featureFileName the name of the file in which features will be
     *                        stored during training
     * @param modelFileName   the name of the file in which the max ent
     *                        model will be stored
     */

    public MaxEntModel(String featureFileName, String modelFileName) {
        this.featureFileName = featureFileName;
        this.modelFileName = modelFileName;
    }

    public void initializeForTraining(String featureFileName) {
        this.featureFileName = featureFileName;
        initializeForTraining();
    }

    public void initializeForTraining() {
        if (featureFileName == null) {
            System.out.println("MaxEntModel.initializeForTraining: no featureFileName specified");
        } else {
            try {
                featureWriter = new PrintStream(new FileOutputStream(featureFileName));
            } catch (IOException e) {
                System.out.print("Unable to create feature file: ");
                System.out.println(e);
            }
        }
    }

    /**
     * invoked during training to add one training Datum <CODE>d</CODE> to the
     * training set.
     */

    public void addEvent(Datum d) {
        if (featureWriter == null)
            initializeForTraining();
        featureWriter.println(d.toString());
    }

    /**
     * sets the feature cutoff.  Features occurring fewer than <CODE>cutoff</CODE>
     * times in the training set are ignored.  Default value is 4.
     */

    public void setCutoff(int cutoff) {
        this.cutoff = cutoff;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public void buildModel() {
        boolean USE_SMOOTHING = false;
        double SMOOTHING_OBSERVATION = 0.1;
        boolean PRINT_MESSAGES = true;
        try {
            featureWriter.close();
            FileReader datafr = new FileReader(new File(featureFileName));
            EventStream es =
                    new BasicEventStream(new PlainTextByLineDataStream(datafr));
            GIS.SMOOTHING_OBSERVATION = SMOOTHING_OBSERVATION;
	    if (USE_L2)
                model = GIS.trainL2Model(es, 0, 2);
	    else
                model = GIS.trainModel(es, iterations, cutoff, USE_SMOOTHING, PRINT_MESSAGES);
        } catch (Exception e) {
            System.out.print("Unable to create model due to exception: ");
            System.out.println(e);
        }
    }

    public void saveModel() {
        if (modelFileName == null) {
            System.out.println("MaxEntModel.saveModel:  no modelFileName specified");
        } else {
            saveModel(modelFileName);
        }
    }

    public void saveModel(String modelFileName) {
        try {
            File outputFile = new File(modelFileName);
            GISModelWriter modelWriter = new SuffixSensitiveGISModelWriter(model, outputFile);
            modelWriter.persist();
        } catch (IOException e) {
            System.out.print("Unable to save model: ");
            System.out.println(e);
        }
    }

    public void saveModel(BufferedWriter writer) {
        try {
            GISModelWriter modelWriter = new PlainTextGISModelWriter(model, writer);
            modelWriter.persist();
        } catch (IOException e) {
            System.out.print("Unable to save model: ");
            System.out.println(e);
        }
    }

    public void loadModel() {
        if (modelFileName == null) {
            System.out.println("MaxEntModel.loadModel:  no modelFileName specified");
        } else {
            loadModel(modelFileName);
        }
    }

    public void loadModel(String modelFileName) {
        try {
            File f = new File(modelFileName);
            model = (GISModel) new SuffixSensitiveGISModelReader(f).getModel();
            System.out.println("GIS model " + f.getName() + " loaded.");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    public void loadModel(BufferedReader reader) {
        try {
            model = (GISModel) new PlainTextGISModelReader(reader).getModel();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    public boolean isLoaded() {
        return model != null;
    }

    /**
     * (for a trained model) returns the probability that the Datum
     * <CODE>d</CODE> is classified as <CODE>value</CODE>.
     */

    public double prob(Datum d, String value) {
        return model.eval(d.toArray())[model.getIndex(value)];
    }

    /**
     * (for a trained model) returns the most likely outcome for Datum
     * <CODE>d</CODE>.
     */

    public String bestOutcome(Datum d) {
        return model.getBestOutcome(model.eval(d.toArray())).intern();
    }

    public int getNumOutcomes() {
        return model.getNumOutcomes();
    }

    public String getOutcome(int i) {
        return model.getOutcome(i);
    }

    public double[] getOutcomeProbabilities(Datum d) {
        return model.eval(d.toArray());
    }
}
