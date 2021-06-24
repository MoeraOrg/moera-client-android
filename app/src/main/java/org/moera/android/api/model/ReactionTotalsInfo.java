package org.moera.android.api.model;

import java.util.ArrayList;
import java.util.List;

public class ReactionTotalsInfo {

    private List<ReactionTotalInfo> positive = new ArrayList<>();
    private List<ReactionTotalInfo> negative = new ArrayList<>();

    public ReactionTotalsInfo() {
    }

    public List<ReactionTotalInfo> getPositive() {
        return positive;
    }

    public void setPositive(List<ReactionTotalInfo> positive) {
        this.positive = positive;
    }

    public List<ReactionTotalInfo> getNegative() {
        return negative;
    }

    public void setNegative(List<ReactionTotalInfo> negative) {
        this.negative = negative;
    }

}
