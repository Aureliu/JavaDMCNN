// -*- tab-width: 4 -*-
package Jet.Chunk;

import opennlp.maxent.*;
import opennlp.maxent.io.*;
import opennlp.model.*;
import java.io.*;

/**
 * Main class which calls the GIS procedure after building the EventStream
 * from the data.
 */

public class CreateModel {

    // some parameters if you want to play around with the smoothing option
    // for model training.  This can improve model accuracy, though training
    // will potentially take longer and use more memory.  Model size will also
    // be larger.  Initial testing indicates improvements for models built on
    // small data sets and few outcomes, but performance degradation for those
    // with large data sets and lots of outcomes.
    static boolean USE_SMOOTHING = false;
    static double SMOOTHING_OBSERVATION = 0.1;
 		static boolean PRINT_MESSAGES = true;

    public static void main (String[] args) {
	// String dataFileName = ChunkBuildTrain.chunkDir + "chunk features.txt";
	// String modelFileName = ChunkBuildTrain.chunkDir + "chunk model.txt";
	String home =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/";
	String dataFileName = home + "jet temp/coref features.txt";
	String modelFileName = home + "jet temp/coref model.txt";
	try {
	    FileReader datafr = new FileReader(new File(dataFileName));
	    EventStream es =
		new BasicEventStream(new PlainTextByLineDataStream(datafr));
	    GIS.SMOOTHING_OBSERVATION = SMOOTHING_OBSERVATION;
	    GISModel model = GIS.trainModel(es, 100, 4, USE_SMOOTHING, PRINT_MESSAGES);
	    File outputFile = new File(modelFileName);
	    GISModelWriter writer = new SuffixSensitiveGISModelWriter(model, outputFile);
	    writer.persist();
	} catch (Exception e) {
	    System.out.print("Unable to create model due to exception: ");
	    System.out.println(e);
	}
    }
}
