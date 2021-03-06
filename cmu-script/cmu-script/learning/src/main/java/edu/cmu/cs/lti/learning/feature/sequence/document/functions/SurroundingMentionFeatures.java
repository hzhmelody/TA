package edu.cmu.cs.lti.learning.feature.sequence.document.functions;

import edu.cmu.cs.lti.learning.feature.sequence.base.SequenceFeatureWithFocus;
import edu.cmu.cs.lti.script.type.EventMention;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import org.apache.uima.jcas.JCas;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Date: 12/31/15
 * Time: 5:44 PM
 *
 * @author Zhengzhong Liu
 */
public class SurroundingMentionFeatures extends SequenceFeatureWithFocus<EventMention> {
    private final String outsideValue = "<OUTSIDE>";

    public SurroundingMentionFeatures(Configuration generalConfig, Configuration featureConfig) {
        super(generalConfig, featureConfig);
    }

    @Override
    public void initDocumentWorkspace(JCas context) {

    }

    @Override
    public void resetWorkspace(JCas aJCas) {

    }

    @Override
    public void extract(List<EventMention> sequence, int focus, TObjectDoubleMap<String> features,
                        TObjectDoubleMap<String> featuresNeedForState) {
        for (int window = 0; window < 3; window++) {
            for (int offset = 0; offset < window; offset++) {
                addToFeatures(features, String.format("MentionHead@i+%d=%s", window, getMentionHead(sequence, focus +
                        window)), 1);
                addToFeatures(features, String.format("MentionHead@i-%d=%s", window, getMentionHead(sequence, focus -
                        window)), 1);
            }
        }
    }

    private String getMentionHead(List<EventMention> sequence, int location) {
        if (location > 0 && location < sequence.size()) {
            EventMention mention = sequence.get(location);
            return UimaNlpUtils.findHeadFromAnnotation(mention).getLemma().toLowerCase();
        }
        return outsideValue;
    }
}
