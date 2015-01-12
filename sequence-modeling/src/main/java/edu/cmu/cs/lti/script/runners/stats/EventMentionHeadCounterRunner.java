package edu.cmu.cs.lti.script.runners.stats;

import edu.cmu.cs.lti.script.annotators.learn.train.KarlMooneyScriptCounter;
import edu.cmu.cs.lti.script.annotators.stats.EventMentoinHeadTfDfCounter;
import edu.cmu.cs.lti.script.utils.DataPool;
import edu.cmu.cs.lti.uima.io.reader.CustomCollectionReaderFactory;
import edu.cmu.cs.lti.uima.io.writer.CustomAnalysisEngineFactory;
import edu.cmu.cs.lti.utils.Configuration;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.uimafit.factory.TypeSystemDescriptionFactory;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: zhengzhongliu
 * Date: 10/25/14
 * Time: 5:30 PM
 */
public class EventMentionHeadCounterRunner {
    private static String className = EventMentionHeadCounterRunner.class.getSimpleName();

    /**
     * @param args
     * @throws java.io.IOException
     * @throws org.apache.uima.UIMAException
     */
    public static void main(String[] args) throws Exception {
        System.out.println(className + " started...");
        Configuration config = new Configuration(new File(args[0]));

        String inputDir = config.get("edu.cmu.cs.lti.cds.event_tuple.path"); //"data/02_event_tuples";
        String dbPath = config.get("edu.cmu.cs.lti.cds.dbpath");
        String blackListFile = config.get("edu.cmu.cs.lti.cds.blacklist"); //"duplicate.count.tail"
        String[] dbNames = config.getList("edu.cmu.cs.lti.cds.db.basenames"); //db names;

        String paramTypeSystemDescriptor = "TypeSystem";

        DataPool.readBlackList(new File(blackListFile));
        DataPool.loadHeadIds(dbPath, dbNames[0], KarlMooneyScriptCounter.defaltHeadIdMapName);

        // Instantiate the analysis engine.
        TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
                .createTypeSystemDescription(paramTypeSystemDescriptor);

        CollectionReaderDescription reader =
                CustomCollectionReaderFactory.createRecursiveGzippedXmiReader(typeSystemDescription, inputDir, false);

        //The coocc and occ counter
//        AnalysisEngineDescription kmScriptCounter = CustomAnalysisEngineFactory.createAnalysisEngine(
//                FastEventMentionHeadCounter.class, typeSystemDescription,
//                FastEventMentionHeadCounter.PARAM_DB_DIR_PATH, dbPath,
//                FastEventMentionHeadCounter.PARAM_KEEP_QUIET, false);

        // The Tf Df counter
        AnalysisEngineDescription headTfDfCounter = CustomAnalysisEngineFactory.createAnalysisEngine(
                EventMentoinHeadTfDfCounter.class, typeSystemDescription,
                EventMentoinHeadTfDfCounter.PARAM_DB_DIR_PATH, dbPath,
                EventMentoinHeadTfDfCounter.PARAM_KEEP_QUIET, false);

        SimplePipeline.runPipeline(reader, headTfDfCounter);
        System.out.println(className + " completed.");
    }
}