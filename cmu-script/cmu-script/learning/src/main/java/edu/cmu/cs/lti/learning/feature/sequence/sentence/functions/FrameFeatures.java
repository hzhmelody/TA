package edu.cmu.cs.lti.learning.feature.sequence.sentence.functions;

import com.google.common.collect.ArrayListMultimap;
import edu.cmu.cs.lti.learning.feature.sequence.FeatureUtils;
import edu.cmu.cs.lti.learning.feature.sequence.base.SequenceFeatureWithFocus;
import edu.cmu.cs.lti.script.type.SemaforAnnotationSet;
import edu.cmu.cs.lti.script.type.SemaforLabel;
import edu.cmu.cs.lti.script.type.SemaforLayer;
import edu.cmu.cs.lti.script.type.StanfordCorenlpToken;
import edu.cmu.cs.lti.uima.util.UimaConvenience;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/6/15
 * Time: 8:48 PM
 *
 * @author Zhengzhong Liu
 */
public class FrameFeatures extends SequenceFeatureWithFocus<StanfordCorenlpToken> {
    ArrayListMultimap<StanfordCorenlpToken, Pair<String, String>> triggerToArgs;
    Map<StanfordCorenlpToken, String> triggerToFrameName;

    List<BiConsumer<TObjectDoubleMap<String>, Pair<String, String>>> argumentTemplates;
    List<BiConsumer<TObjectDoubleMap<String>, String>> frameTemplates;

    public FrameFeatures(Configuration generalConfig, Configuration featureConfig) {
        super(generalConfig, featureConfig);
        argumentTemplates = new ArrayList<>();
        frameTemplates = new ArrayList<>();

        for (String templateName : featureConfig.getList(this.getClass().getSimpleName() + ".templates")) {
            switch (templateName) {
                case "FrameArgumentLemma":
                    argumentTemplates.add(this::frameArgumentLemma);
                    break;
                case "FrameArgumentRole":
                    argumentTemplates.add(this::frameArgumentRole);
                    break;
                case "FrameName":
                    frameTemplates.add(this::frameName);
                    break;
                default:
                    logger.warn(String.format("Template [%s] not recognized.", templateName));
            }
        }
    }

    @Override
    public void initDocumentWorkspace(JCas context) {
        readFrames(context);
    }

    @Override
    public void resetWorkspace(JCas aJCas) {
    }

    @Override
    public void extract(List<StanfordCorenlpToken> sequence, int focus, TObjectDoubleMap<String> features,
                        TObjectDoubleMap<String> featuresNeedForState) {
        if (focus > sequence.size() - 1 || focus < 0) {
            return;
        }
        StanfordCorenlpToken token = sequence.get(focus);

        if (triggerToArgs.containsKey(token)) {
            for (Pair<String, String> triggerAndType : triggerToArgs.get(token)) {
                for (BiConsumer<TObjectDoubleMap<String>, Pair<String, String>> argumentTemplate : argumentTemplates) {
                    argumentTemplate.accept(features, triggerAndType);
                }
            }
        }

        if (triggerToFrameName.containsKey(token)) {
            for (BiConsumer<TObjectDoubleMap<String>, String> frameTemplate : frameTemplates) {
                frameTemplate.accept(features, triggerToFrameName.get(token));
            }
        }
    }

    // Feature templates.
    private void frameName(TObjectDoubleMap<String> features, String frameName) {
        addToFeatures(features, FeatureUtils.formatFeatureName("FrameName", frameName), 1);
    }

    private void frameArgumentLemma(TObjectDoubleMap<String> features, Pair<String, String> triggerAndType) {
        addToFeatures(features, FeatureUtils.formatFeatureName("FrameArgumentLemma", triggerAndType.getValue0()), 1);
    }

    private void frameArgumentRole(TObjectDoubleMap<String> features, Pair<String, String> triggerAndType) {
        addToFeatures(features, FeatureUtils.formatFeatureName("FrameArgumentRole", triggerAndType.getValue1()), 1);
    }

    // Prepare frames.
    private void readFrames(JCas jCas) {
        triggerToArgs = ArrayListMultimap.create();
        triggerToFrameName = new HashMap<>();
        for (SemaforAnnotationSet annoSet : JCasUtil.select(jCas, SemaforAnnotationSet.class)) {
            String frameName = annoSet.getFrameName();

            SemaforLabel trigger = null;
            List<SemaforLabel> frameElements = new ArrayList<>();

            for (SemaforLayer layer : FSCollectionFactory.create(annoSet.getLayers(), SemaforLayer.class)) {
                String layerName = layer.getName();
                if (layerName.equals("Target")) {// Target that invoke the frame
                    trigger = layer.getLabels(0);
                } else if (layerName.equals("FE")) {// Frame element
                    FSArray elements = layer.getLabels();
                    if (elements != null) {
                        frameElements.addAll(FSCollectionFactory.create(elements, SemaforLabel.class).stream()
                                .collect(Collectors.toList()));
                    }
                }
            }

            StanfordCorenlpToken triggerHead = UimaNlpUtils.findHeadFromAnnotation(trigger);
            if (triggerHead == null) {
                triggerHead = UimaConvenience.selectCoveredFirst(trigger, StanfordCorenlpToken.class);
            }
            if (triggerHead != null) {
                triggerToFrameName.put(triggerHead, frameName);
            }

            for (SemaforLabel label : frameElements) {
                StanfordCorenlpToken elementHead = UimaNlpUtils.findHeadFromAnnotation(label);
                if (elementHead == null) {
                    elementHead = UimaConvenience.selectCoveredFirst(label, StanfordCorenlpToken.class);
                }
                if (elementHead != null) {
                    triggerToArgs.put(elementHead, Pair.with(elementHead.getLemma(), label.getName()));
                }
            }
        }
    }
}
