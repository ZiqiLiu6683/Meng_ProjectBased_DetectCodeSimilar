package com.ziqi.codesim.region.model;

/**
 * A directed, typed dependence edge between two {@link SemanticNode}s of the same
 * {@link SemanticGraph}, referenced by their graph-local ids.
 *
 * @param fromId source node id
 * @param toId   target node id
 * @param kind   dependence kind (see {@link EdgeKind})
 */
public record SemanticEdge(int fromId, int toId, EdgeKind kind) {

    public boolean isInterprocedural() {
        return switch (kind) {
            case CALL, PARAM_IN, PARAM_OUT, RETURN, INTERPROC_OTHER -> true;
            case CONTROL_DEP, DATA_DEP, DEPENDENCE -> false;
        };
    }
}
