// Ziqi Liu Meng Project-Based Software Engineering
// Lightweight ordered tree node used by the Tree Edit Distance (TED) algorithm.
// Kept intentionally simple and decoupled from JavaParser so that
// TreeEditDistance can be tested and reasoned about independently.
package com.ziqi.codesim.ast;

import java.util.ArrayList;
import java.util.List;

public class TedNode {

    /** Normalised node label (e.g. "IfStmt", "ID", "NUM", "T:int"). */
    public final String label;

    /** Ordered list of child nodes (order matters for TED on ordered trees). */
    public final List<TedNode> children = new ArrayList<>();

    private int cachedSize = -1;

    public TedNode(String label) {
        this.label = label;
    }

    public void addChild(TedNode child) {
        children.add(child);
        cachedSize = -1; // invalidate cache
    }

    /** Number of nodes in this subtree (self + all descendants). */
    public int size() {
        if (cachedSize >= 0) return cachedSize;
        cachedSize = 1;
        for (TedNode c : children) cachedSize += c.size();
        return cachedSize;
    }

    @Override
    public String toString() {
        return label + (children.isEmpty() ? "" : children.toString());
    }
}
