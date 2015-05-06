package edu.cmu.lti.event_coref.model.graph;

import com.google.common.collect.ArrayListMultimap;
import edu.cmu.cs.lti.feature.MapBasedFeatureContainer;
import edu.cmu.cs.lti.script.type.Event;
import edu.cmu.cs.lti.script.type.EventMention;
import edu.cmu.cs.lti.script.type.EventMentionRelation;
import edu.cmu.lti.event_coref.ml.MentionPairFeatureExtractor;
import edu.cmu.lti.event_coref.ml.StructWeights;
import edu.cmu.lti.event_coref.model.graph.Edge.EdgeType;
import gnu.trove.map.TObjectDoubleMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * Date: 5/1/15
 * Time: 11:30 PM
 *
 * @author Zhengzhong Liu
 */
public class Graph {
    private Node[] nodes;

    //Edge is indexed from the anaphora to its antecedent
    private Edge[][] edges;

    private int numEdges;

    //represent each cluster as one array, sorted on head, and within cluster
    private int[][] corefChains;

    //represent each relation with a adjacent list
    private Map<EdgeType, int[][]> edgeAdjacentList;

    public Graph(List<EventMention> mentions, List<EventMentionRelation> relations) {
        nodes = new Node[mentions.size() + 1];
        //a virtual root
        nodes[0] = new Node(0);

        ArrayListMultimap<Event, Integer> event2Clusters = groupEventClusters(mentions);
        //this will only store non-singleton to corefChains
        corefChains = GraphUtils.createSortedCorefChains(event2Clusters);
        //this will store all relations
        edgeAdjacentList = GraphUtils.resolveRelations(generalizeRelationNode(relations), event2Clusters, nodes.length);

        edges = new Edge[nodes.length - 1][];
        for (int curr = 1; curr < nodes.length; curr++) {
            edges[curr] = new Edge[curr];
            for (int ant = 0; ant < curr; ant++) {
                edges[curr][ant] = new Edge(ant, curr);
            }
        }

        //store all golden edge type into edge
        for (int[] chain : corefChains) {
            for (int i = 0; i < chain.length - 1; i++) {
                int antecedentId = chain[i];
                for (int j = i + 1; j < chain.length; j++) {
                    int anaphoraId = chain[j];
                    edges[antecedentId][anaphoraId].edgeType = EdgeType.Coreference;
                }
            }
        }

        for (Map.Entry<EdgeType, int[][]> edgeRelations : edgeAdjacentList.entrySet()) {
            EdgeType type = edgeRelations.getKey();
            int[][] adjacentList = edgeRelations.getValue();

            for (int govNodeId = 0; govNodeId < adjacentList.length; govNodeId++) {
                for (int depNodeId : adjacentList[govNodeId]) {
                    edges[govNodeId][depNodeId].edgeType = type;
                }
            }
        }
    }

    private ArrayListMultimap<Event, Integer> groupEventClusters(List<EventMention> mentions) {
        ArrayListMultimap<Event, Integer> event2Clusters = ArrayListMultimap.create();
        for (int i = 1; i < nodes.length; i++) {
            EventMention mention = mentions.get(i - 1);
            nodes[i] = new Node(i, mention);
            Event event = mention.getReferringEvent();
            //this will store all clusters, including singleton clusters
            event2Clusters.put(event, i);
        }
        return event2Clusters;
    }

    private ArrayListMultimap<EdgeType, Pair<Event, Event>> generalizeRelationNode(List<EventMentionRelation> relations) {
        ArrayListMultimap<EdgeType, Pair<Event, Event>> allRelations = ArrayListMultimap.create();
        for (EventMentionRelation relation : relations) {
            EventMention govMention = relation.getHead();
            EventMention depMention = relation.getChild();
            EdgeType type = EdgeType.valueOf(relation.getRelationType());
            allRelations.put(type, Pair.of(govMention.getReferringEvent(), depMention.getReferringEvent()));
        }
        return allRelations;
    }

    public Node getNode(int index) {
        return nodes[index];
    }

    public Node[] getNodes() {
        return nodes;
    }

    public Edge[][] getEdges() {
        return edges;
    }

    public int numNodes() {
        return nodes.length;
    }

    public int getNumEdges() {
        return numEdges;
    }

    public int[][] getCorefChains() {
        return corefChains;
    }

    public Map<EdgeType, int[][]> getEdgeAdjacentList() {
        return edgeAdjacentList;
    }

    public void fillGraph(MentionPairFeatureExtractor extractor, StructWeights weights) {
        //TODO make this multi-thread
        // Might move this to graph class instead
        for (Edge[] edges : this.getEdges()) {
            for (Edge edge : edges) {
                Node depNode = this.getNode(edge.depIdx);
                Node govNode = this.getNode(edge.govIdx);
                extractor.computeFeatures(depNode.getMention(), govNode.getMention());
                //set unlabelled score and features
                TObjectDoubleMap<String> arcFeatures = extractor.getUnlablledFeatures();
                edge.setArcScore(weights.unlabelledWeights.score(arcFeatures));
                edge.setArcFeatures(arcFeatures);

                //set labelled scores and features
                EnumMap<EdgeType, TObjectDoubleMap<String>> labelledFeatures = extractor.getLabelledFeatures();
                for (Map.Entry<EdgeType, MapBasedFeatureContainer> edgeTypeWeights : weights.labelledWeights.entrySet()) {
                    EdgeType eType = edgeTypeWeights.getKey();
                    TObjectDoubleMap<String> typeFeatures = labelledFeatures.get(eType);
                    edge.setLabelScore(eType, weights.labelledWeights.get(eType).score(typeFeatures));
                    edge.setLabelledFeatures(labelledFeatures);
                }
            }
        }
    }
}
