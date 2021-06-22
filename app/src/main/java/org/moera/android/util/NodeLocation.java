package org.moera.android.util;

public class NodeLocation {

    private String nodeName;
    private String href;

    public NodeLocation(String nodeName, String href) {
        this.nodeName = nodeName;
        this.href = href;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

}
