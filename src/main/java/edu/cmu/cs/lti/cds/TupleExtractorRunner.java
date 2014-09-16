/**
 * 
 */
package edu.cmu.cs.lti.cds;

import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.examples.xmi.XmiCollectionReader;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.uimafit.factory.TypeSystemDescriptionFactory;

import edu.cmu.cs.lti.cds.annotators.EventTupleExtractor;
import edu.cmu.cs.lti.uima.io.writer.CustomAnalysisEngineFactory;

/**
 * @author zhengzhongliu
 * 
 */
public class TupleExtractorRunner {
  private static String className = TupleExtractorRunner.class.getSimpleName();

  /**
   * @param args
   * @throws IOException
   * @throws UIMAException
   */
  public static void main(String[] args) throws UIMAException, IOException {
    System.out.println(className + " started...");

    // ///////////////////////// Parameter Setting ////////////////////////////
    // Note that you should change the parameters below for your configuration.
    // //////////////////////////////////////////////////////////////////////////
    // Parameters for the reader
    String paramInputDir = "data/00_xmi";

    // Parameters for the writer
    String paramParentOutputDir = "data";
    String paramBaseOutputDirName = "xmi";
    String paramOutputFileSuffix = null;

    String paramModelBaseDirectory = "/Users/zhengzhongliu/Documents/projects/uimafied-tools/fanse-parser/src/main/resources/";
    // ////////////////////////////////////////////////////////////////

    String paramTypeSystemDescriptor = "TypeSystem";

    // Instantiate the analysis engine.
    TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
            .createTypeSystemDescription(paramTypeSystemDescriptor);

    // Instantiate a collection reader to get XMI as input.
    // Note that you should change the following parameters for your setting.
    CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
            XmiCollectionReader.class, typeSystemDescription, XmiCollectionReader.PARAM_INPUTDIR,
            paramInputDir);

    AnalysisEngineDescription tupleExtractor = CustomAnalysisEngineFactory.createAnalysisEngine(
            EventTupleExtractor.class, typeSystemDescription);

    // Instantiate a XMI writer to put XMI as output.
    // Note that you should change the following parameters for your setting.
    AnalysisEngineDescription writer = CustomAnalysisEngineFactory.createXmiWriter(
            paramParentOutputDir, paramBaseOutputDirName, 1, paramOutputFileSuffix);

    // Run the pipeline.
    // SimplePipeline.runPipeline(reader, writer);
    SimplePipeline.runPipeline(reader, tupleExtractor, writer);

    System.out.println(className + " completed.");
  }

}
