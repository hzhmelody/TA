package edu.cmu.lti.event_coref.model.graph;

import edu.cmu.cs.lti.script.type.EventMention;

/**
 * Created with IntelliJ IDEA.
 * Date: 5/1/15
 * Time: 11:28 PM
 *
 * @author Zhengzhong Liu
 */
public class Node {
    EventMention mention;
    int id;

    boolean isVirtual;

    public Node(int id) {
        this.id = id;
        this.mention = null;
        isVirtual = true;
    }

    public Node(int id, EventMention mention) {
        this.id = id;
        this.mention = mention;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public void setIsVirtual(boolean isVirtual) {
        this.isVirtual = isVirtual;
    }

    public boolean isRoot() {
        return id == 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public EventMention getMention() {
        return mention;
    }

    public void setMention(EventMention mention) {
        this.mention = mention;
    }
}
