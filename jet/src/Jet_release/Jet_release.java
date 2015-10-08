package Jet_release;

import java.io.IOException;
import AceJet.Ace;
import AceJet.AureDiTrainData;
import AceJet.EventScorer;
import AceJet.EventTypeAnalysis;
import AceJet.SentenceAnalysis;
import AceJet.TrainEventTagger;

public class Jet_release {

    /**
     * @param args the command line arguments
     * @args[0]: train or test
     * @args[1]: property file
     * @args[2]: the list of test file
     * @args[3]: the folder of test files
     * @args[4]: the output folder
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        // TODO code application logic here
//        args = new String[5];
//        args[0] = "-train";
//        args[1] = "C:\\Users\\v-lesha\\Documents\\NetBeansProjects\\RBET_release\\props\\ace11chunker.properties";
//        args[2] = "D:\\LDC2006D06\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\nw\\Trainfile.txt";
//        args[3] = "D:\\LDC2006D06\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\nw\\fp1\\";
//        args[4] = "C:\\Users\\v-lesha\\Documents\\NetBeansProjects\\RBET_release\\Trainout\\";


//        args = new String[5];
//        args[0] = "-test";
//        args[1] = "D:\\GitHub\\JavaDMCNN\\jet\\props\\ace11chunker.properties";
//        args[2] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\nw\\fp1\\Testfile.txt";
//        args[3] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\nw\\fp1\\";
//        args[4] = "D:\\GitHub\\JavaDMCNN\\jet\\output\\";

//        args = new String[5];
//        args[0] = "-test";
//        args[1] = "D:\\GitHub\\JavaDMCNN\\jet\\props\\ace11chunker.properties";
//        args[2] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\nw\\fp1\\Testfile.txt";
//        args[3] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\nw\\fp1\\";
//        args[4] = "D:\\GitHub\\JavaDMCNN\\jet\\output\\";


//        args = new String[8];
//        args[0] = "-score";
//        args[1] = "D:\\WorkSpace\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\nw\\fp1\\Testfile.txt";
//        args[2] = "D:\\WorkSpace\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\nw\\fp1";
//        args[3] = "sgm";
//        args[4] = "D:\\GitHub\\JavaDMCNN\\jet\\output";
//        args[5] = "sgm.apf";      
//        args[6] = "D:\\WorkSpace\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\nw\\fp1";
//        args[7] = "apf.xml";
    	
//      args = new String[6];
//      args[0] = "-EventTypeAnalysis";

//      args[1] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\ACE_List";
//      args[2] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//      args[3] = "sgm";    
//      args[4] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";

//      args[1] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\ACE_List";
//      args[2] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//      args[3] = "sgm";    
//      args[4] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";

//      args[5] = "apf.xml";
        
//		args = new String[6];
//		args[0] = "-SentenceAnalysis";

//		args[1] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\test";
//		args[2] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//		args[3] = "sgm";    
//		args[4] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//		args[5] = "apf.xml";   
    	
//      args[1] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\ACE_List";
//      args[2] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//      args[3] = "sgm";    
//      args[4] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";

//		args[1] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\ACE_List";
//		args[2] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//		args[3] = "sgm";    
//		args[4] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//		args[5] = "apf.xml";   
    	
//      args[1] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\ACE_List";
//      args[2] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//      args[3] = "sgm";    
//      args[4] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";

//		args[1] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\ACE_List";
//		args[2] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//		args[3] = "sgm";    
//		args[4] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//		args[5] = "apf.xml";   
    	
//      args[1] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\ACE_List";
//      args[2] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
//      args[3] = "sgm";    
//      args[4] = "D:\\Event Mention\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";

//      args[5] = "apf.xml";
		
		args = new String[6];
		args[0] = "-AureDiTrainData";
		args[1] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English\\new_filelist_ACE_test";//new_filelist_ACE_training";
		args[2] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
		args[3] = "sgm";    
		args[4] = "D:\\Corpus\\LDC2006D06\\Data\\LDC2006T06_Original\\data\\English";
		args[5] = "apf.xml"; 
        
        if (args.length != 5 && args.length != 6 && args.length != 8) {
            PrintErrMsg();
            System.exit(1);
        }
        switch (args[0]) {
            case "-train":
                //Ace.Testing = false;
                String[] train_args = new String[4];
                for (int i = 0; i < 4; i++) {
                    train_args[i] = args[i + 1];
                }
                TrainEventTagger.main(train_args);
                break;
            case "-test":
                String[] test_args = new String[4];
                for (int i = 0; i < 4; i++) {
                    test_args[i] = args[i + 1];
                }
                Ace.main(test_args);
                break;    
            case "-score":
                String[] score_args = new String[7];
                for (int i = 0; i < 7; i++) {
                    score_args[i] = args[i + 1];
                }
                EventScorer.main(score_args);
                break;
            case "-EventTypeAnalysis":
                String[] EventTypeAnalysis_args = new String[5];
                for (int i = 0; i < 5; i++) {
                	EventTypeAnalysis_args[i] = args[i + 1];
                }
                EventTypeAnalysis.main(EventTypeAnalysis_args);
                break;
            case "-SentenceAnalysis":
                String[] SentenceAnalysis_args = new String[5];
                for (int i = 0; i < 5; i++) {
                	SentenceAnalysis_args[i] = args[i + 1];
                }
                SentenceAnalysis.main(SentenceAnalysis_args);
                break;
            case "-AureDiTrainData":
                String[] AureDiTrainData_args = new String[5];
                for (int i = 0; i < 5; i++) {
                	AureDiTrainData_args[i] = args[i + 1];
                }
                AureDiTrainData.main(AureDiTrainData_args);
                break;
            default:
                PrintErrMsg();
                break;
        }
        
    }
    
    private static void PrintErrMsg() {
        System.err.print("Input format error!\n"
                + "\n"
                + "Correct format:\n"
                + "	JET_release -train properties trainfilelist traindocumentDir ModeloutputDir\n"
                + "or\n"
                + "	JET_release -test properties testfilelist testdocumentDir testoutputDir\n");

    }

}
