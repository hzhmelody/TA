package edu.cmu.cs.lti.event_coref.model.graph;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import edu.cmu.cs.lti.event_coref.model.graph.MentionGraphEdge.EdgeType;
import edu.cmu.cs.lti.learning.feature.mention_pair.extractor.PairFeatureExtractor;
import edu.cmu.cs.lti.learning.model.GraphWeightVector;
import edu.cmu.cs.lti.learning.model.MentionCandidate;
import edu.cmu.cs.lti.learning.model.NodeKey;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * Date: 5/1/15
 * Time: 11:30 PM
 *
 * @author Zhengzhong Liu
 */
public class MentionGraph implements Serializable {
    private static final long serialVersionUID = -3451529942657683816L;

    protected transient final Logger logger = LoggerFactory.getLogger(getClass());

    // Extractor must be passed in if deserialized.
    private transient PairFeatureExtractor extractor;

    // Edge represent a relation from a node to the other indexed from the dependent node to the governing node.
    // For coreference, dependent is always the anaphora (later), governer is its antecedent (earlier).
    // For relations, this dependents on the link direction. Link come form the governer and end at the dependent.
    //
    // This edge can be used to store edge features and labelled scores, and the gold type. For edges that are not in
    // gold standard, the gold type will be null.
    private MentionGraphEdge[][] graphEdges;

    // Represent each cluster as one array. Each chain is sorted by id within cluster. Chains are sorted by their
    // first element.
    // This chain is used to store the gold standard coreference chains during training. Decoding chains are obtained
    // via a similar field in the subgraph.
//    private int[][] nodeCorefChains;
    private List<Pair<Integer, String>>[] typedCorefChains;

    // Represent each relation with a adjacent list.
    private Map<EdgeType, ListMultimap<Pair<Integer, String>, Pair<Integer, String>>> resolvedRelations;

    private boolean useAverage = false;

    private final int rootIndex = 0;

    private final int numNodes;

    /**
     * Provide to the graph with only a list of mentions, no coreference information.
     */
    public MentionGraph(List<MentionCandidate> mentions, PairFeatureExtractor extractor, boolean useAverage) {
        this(mentions, HashMultimap.create(), new ArrayList<>(), new HashMap<>(), new HashMap<>(), extractor, false);
        this.useAverage = useAverage;
    }

    /**
     * Provide to the graph a list of mentions and predefined event relations. Nodes without these relations will be
     * link to root implicitly.
     *
     * @param candidates                   List of candidates for linking. Each candidate corresponds to a unique span.
     * @param candidate2Labelled           Map from candidate to its labelled version, one candidate can have multiple
     *                                     labelled counter part.
     * @param labelledCandidateTypes       Type for each labelled candidate.
     * @param labelledCandidate2EventIndex Event for each labelled candidate.
     * @param relations                    Relation between labelled candidate.
     * @param extractor                    The feature extractor.
     */
    public MentionGraph(List<MentionCandidate> candidates, SetMultimap<Integer, Integer> candidate2Labelled,
                        List<String> labelledCandidateTypes, Map<Integer, Integer> labelledCandidate2EventIndex,
                        Map<Pair<Integer, Integer>, String> relations, PairFeatureExtractor extractor,
                        boolean isTraining) {
        this.extractor = extractor;
        numNodes = candidates.size() + 1;

        // Initialize the edges.
        // Edges are 2-d arrays, from current to antecedent. The first node (root), does not have any antecedent,
        // nor link to any other relations. So it is a empty array. Node 0 has no edges.
        graphEdges = new MentionGraphEdge[numNodes][];
        for (int curr = 1; curr < numNodes; curr++) {
            graphEdges[curr] = new MentionGraphEdge[numNodes];
        }

        if (isTraining) {
            this.useAverage = false;

            // Candidates are the index for the actual tokens (or spans) in text, on one candidate there might be
            // multiple mentions.
            // Mentions are real annotated mentions, where multiple mentions can corresponds to one single candidate.
            // Nodes is almost corresponded to candidates, we just add one more root node.
            TIntIntMap mention2Nodes = new TIntIntHashMap();
            for (Map.Entry<Integer, Collection<Integer>> candidate2Mentions : candidate2Labelled.asMap().entrySet()) {
                int nodeIndex = getNodeIndex(candidate2Mentions.getKey());
                for (Integer mention : candidate2Mentions.getValue()) {
                    mention2Nodes.put(mention, nodeIndex);
                }
            }

            // Each cluster is represented as a mapping from the event id to the event mention id list.
            SetMultimap<Integer, Pair<Integer, String>> nodeClusters = HashMultimap.create();
            SetMultimap<Integer, Integer> labelledClusters = HashMultimap.create();

            groupEventClusters(labelledCandidate2EventIndex, candidate2Labelled, labelledCandidateTypes, nodeClusters,
                    labelledClusters);

            // Group mention nodes into clusters, the first is the event id, the second is the node id.
            typedCorefChains = GraphUtils.createSortedCorefChains(nodeClusters);

            storeCoreferenceEdges(candidates);

            // This will store all other relations, which are propagated using the gold clusters.
            resolvedRelations = GraphUtils.resolveRelations(
                    convertToEventRelation(relations, labelledCandidate2EventIndex), nodeClusters);

            // Link lingering nodes to root.
            linkToRoot(candidates);
        }
    }

    private MentionGraphEdge getEdge(int curr, int ant) {
        MentionGraphEdge edge;
        if (graphEdges[curr][ant] == null) {
            edge = new MentionGraphEdge(this, extractor, ant, curr, useAverage);
            graphEdges[curr][ant] = edge;
        } else {
            edge = graphEdges[curr][ant];
        }
        return edge;
    }

    private MentionGraphEdge createLabelledGoldEdge(int gov, int dep, NodeKey realGovKey,
                                                    NodeKey realDepKey, List<MentionCandidate> candidates,
                                                    EdgeType edgeType) {
        MentionGraphEdge goldEdge = graphEdges[dep][gov];

        if (goldEdge == null) {
            goldEdge = new MentionGraphEdge(this, extractor, gov, dep, useAverage);
            graphEdges[dep][gov] = goldEdge;
        }

        goldEdge.addRealEdge(candidates, realGovKey, realDepKey, edgeType);


//        if (!edgeType.equals(EdgeType.Root) ) {
//            logger.debug(String.format("Creating gold edge between %d [%s] and %d [%s]", gov, realGovKey, dep,
//                    realDepKey));
//            logger.debug(newEdge.toString());
//            logger.debug("Size of the labelled edge is now " + newEdge.getRealLabelledEdges().size());
//        }

//        if (dep == 238) {
//            logger.debug(String.format("Creating gold edge between %d [%s] and %d [%s]", gov, realGovKey, dep,
//                    realDepKey));
//            logger.debug(goldEdge.toString());
//            logger.debug("Size of the labelled edge is now " + goldEdge.getRealLabelledEdges().size());
//        }

        return goldEdge;
    }

    /**
     * Store coreference information as graph edges.
     */
    private void storeCoreferenceEdges(List<MentionCandidate> candidates) {
        for (List<Pair<Integer, String>> typedCorefChain : typedCorefChains) {
            // In each chain, we only connect at most one labelled node, we need to record them.
            List<NodeKey> actualCorefNodes = new ArrayList<>();

            for (int i = 0; i < typedCorefChain.size(); i++) {
                Pair<Integer, String> element = typedCorefChain.get(i);
                int nodeId = element.getValue0();
                int candidateIndex = getCandidateIndex(nodeId);
                String mentionType = element.getValue1();

                List<NodeKey> candidateKeys = candidates.get(candidateIndex).asKey();
                NodeKey actualCandidate = null;
                for (NodeKey candidate : candidateKeys) {
                    if (candidate.getMentionType().equals(mentionType)) {
                        actualCandidate = candidate;
                        break;
                    }
                }
                actualCorefNodes.add(actualCandidate);
            }

            // Within the cluster, link each antecedent with all its anaphora.
            for (int i = 0; i < typedCorefChain.size() - 1; i++) {
                int antecedentNode = typedCorefChain.get(i).getValue0();
                NodeKey actualAntecedent = actualCorefNodes.get(i);
                for (int j = i + 1; j < typedCorefChain.size(); j++) {
                    int anaphoraNode = typedCorefChain.get(j).getValue0();
                    NodeKey actualAnaphora = actualCorefNodes.get(j);

                    MentionGraphEdge edge = createLabelledGoldEdge(antecedentNode, anaphoraNode, actualAntecedent,
                            actualAnaphora, candidates, EdgeType.Coreference);
                }
            }
        }

//        DebugUtils.pause(logger);
    }

    /**
     * If a node is link to nowhere, link it to root.
     */
    private void linkToRoot(List<MentionCandidate> candidates) {
//        logger.debug("Linking to root.");
        // Loop starts from 1, because node 0 is the root itself.
        for (int curr = 1; curr < numNodes(); curr++) {
            List<NodeKey> depKeys = candidates.get(getCandidateIndex(curr)).asKey();
            boolean hasEdge = false;
            List<LabelledMentionGraphEdge> realEdges = null;
            for (int ant = 0; ant < curr; ant++) {
                realEdges = getEdge(curr, ant).getRealLabelledEdges();
                if (realEdges.size() > 0) {
//                    logger.debug("have a real edge here " + realEdge);
                    hasEdge = true;
                    break;
                }
            }

            NodeKey rootKey = MentionCandidate.getRootKey().get(0);
            if (hasEdge) {
                for (NodeKey depKey : depKeys) {
                    if (!realEdges.stream().anyMatch(e -> e.getDepKey().equals(depKey))) {
                        createLabelledGoldEdge(0, curr, rootKey, depKey, candidates, EdgeType.Root);
                    }
                }
            } else {
                for (NodeKey depKey : depKeys) {
                    createLabelledGoldEdge(0, curr, rootKey, depKey, candidates, EdgeType.Root);
//                    logger.debug("Linking " + curr + " " + depKey + " to root.");
//                    logger.debug(edge.getLabelledEdge(candidates, rootKey, depKey).toString());
                }
            }
        }
    }

    /**
     * Read the stored event cluster information, stored as a map from event index (cluster index) to mention node
     * index, where event mention indices are based on their index in the input list. Note that mention node index is
     * not the same to mention index because it include artificial nodes (e.g. Root).
     */
    private void groupEventClusters(Map<Integer, Integer> labelled2EventIndex,
                                    SetMultimap<Integer, Integer> candidate2Labelled,
                                    List<String> labelledTypes,
                                    SetMultimap<Integer, Pair<Integer, String>> nodeClusters,
                                    SetMultimap<Integer, Integer> labelledClusters) {
//        ArrayListMultimap<Integer, Integer> event2Clusters = ArrayListMultimap.create();
        for (int nodeIndex = 0; nodeIndex < numNodes(); nodeIndex++) {
            if (!isRoot(nodeIndex)) {
                int candidateIndex = getCandidateIndex(nodeIndex);
                // A candidate can be mapped to multiple mentions (due to multi-tagging). Hence, a cluster can
                // potentially contains many nodes.
//                logger.debug("Candidate index " + candidateIndex);
                for (Integer labelledIndex : candidate2Labelled.get(candidateIndex)) {
//                    logger.debug("Mention index " + mentionIndex);
                    if (labelled2EventIndex.containsKey(labelledIndex)) {
                        int eventIndex = labelled2EventIndex.get(labelledIndex);
                        nodeClusters.put(eventIndex, Pair.with(nodeIndex, labelledTypes.get(labelledIndex)));
                        labelledClusters.put(eventIndex, labelledIndex);
//                        logger.debug("Putting " + eventIndex + " " + mentionIndex);
                    }
                }
            }
        }
    }

    /**
     * Convert event mention relation to event relations. Input relations must be transferable to its corresponding
     * events.
     *
     * @param relations Relations between event mentions.
     * @return Map from edge type to event-event relation.
     */
    private HashMultimap<MentionGraphEdge.EdgeType, Pair<Integer, Integer>> convertToEventRelation(
            Map<Pair<Integer, Integer>, String> relations, Map<Integer, Integer> mention2EventIndex) {
        HashMultimap<MentionGraphEdge.EdgeType, Pair<Integer, Integer>> allRelations = HashMultimap.create();
        for (Map.Entry<Pair<Integer, Integer>, String> relation : relations.entrySet()) {
            int govMention = relation.getKey().getValue0();
            int depMention = relation.getKey().getValue1();
            MentionGraphEdge.EdgeType type = MentionGraphEdge.EdgeType.valueOf(relation.getValue());
            allRelations.put(type, Pair.with(mention2EventIndex.get(govMention), mention2EventIndex.get(depMention)));
        }
        return allRelations;
    }

//    public MentionNode getNode(int index) {
//        return mentionNodes[index];
//    }

//    public MentionNode[] getMentionNodes() {
//        return mentionNodes;
//    }

    public MentionGraphEdge getMentionGraphEdge(int dep, int gov) {
        if (dep == 0) {
            return null;
        }
        return getEdge(dep, gov);
    }

    public int numNodes() {
        return numNodes;
    }

    public List<Pair<Integer, String>>[] getNodeCorefChains() {
        return typedCorefChains;
    }

    public Map<EdgeType, ListMultimap<Pair<Integer, String>, Pair<Integer, String>>> getResolvedRelations() {
        return resolvedRelations;
    }

    public MentionSubGraph getLatentTree(GraphWeightVector weights, List<MentionCandidate> mentionCandidates) {
        return getSubLatentTree(weights, mentionCandidates, numNodes);
    }

    public MentionSubGraph getSubLatentTree(GraphWeightVector weights, List<MentionCandidate> mentionCandidates,
                                            int limit) {
        // TODO: check boundary case of limit.
        MentionSubGraph latentTree = new MentionSubGraph(this, limit);

        for (int curr = 1; curr < limit; curr++) {
            Pair<LabelledMentionGraphEdge, MentionGraphEdge.EdgeType> bestEdge = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            int currMentionIndex = getCandidateIndex(curr);

            List<NodeKey> currKeys = mentionCandidates.get(currMentionIndex).asKey();

//            System.out.println("Finding best edge for " + curr);

            for (int ant = 0; ant < curr; ant++) {
//                System.out.println("Finding best antecedent at " + ant);

                int antMentionIndex = getCandidateIndex(ant);

                List<NodeKey> antKeys = isRoot(ant) ? MentionCandidate.getRootKey() :
                        mentionCandidates.get(antMentionIndex).asKey();

                for (NodeKey antKey : antKeys) {
                    for (NodeKey currKey : currKeys) {
                        LabelledMentionGraphEdge goldEdge = graphEdges[curr][ant].getLabelledEdge(
                                mentionCandidates,
                                // In lat only training, these are gold types.
                                // In joint training, these could be predicted types.
                                antKey, currKey
                        );

                        if (goldEdge != null) {// TODO check null maybe redundant
                            Pair<MentionGraphEdge.EdgeType, Double> correctLabelScore = goldEdge.getCorrectLabelScore
                                    (weights);
//                    System.out.println("Correct label score is  " + correctLabelScore);

                            if (correctLabelScore == null) {
                                // This is not a gold standard edge.
                                continue;
                            }

                            MentionGraphEdge.EdgeType label = correctLabelScore.getValue0();

                            double score = correctLabelScore.getValue1();

//                    System.out.println("Best label is for " + curr + " " + ant + " " + label);

                            if (score > bestScore) {
                                bestEdge = Pair.with(goldEdge, label);
                                bestScore = score;
                            }
                        }
                    }
                }
            }

//            logger.info("Best is " + bestEdge);
            // If you see NULL here, it is likely that something wrong happens with the weights.
            latentTree.addEdge(bestEdge.getValue0(), bestEdge.getValue1());
        }
        return latentTree;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph : \n");

        for (MentionGraphEdge[] mentionGraphEdgeArray : graphEdges) {
            if (mentionGraphEdgeArray != null) {
                for (MentionGraphEdge mentionGraphEdge : mentionGraphEdgeArray) {
                    if (mentionGraphEdge != null) {
                        sb.append("\t").append(mentionGraphEdge.toString()).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    public void setExtractor(PairFeatureExtractor extractor) {
        this.extractor = extractor;
    }

    public int getCandidateIndex(int nodeIndex) {
        // Under the current implementation, we only have an additional root node.
        return nodeIndex - 1;
    }

    public int getNodeIndex(int candidateIndex) {
        return candidateIndex + 1;
    }

    public boolean isRoot(int nodeIndex) {
        return nodeIndex == rootIndex;
    }
}
