package org.moera.android.settings.type;

public class IntSettingTypeModifiers {

    private long min;
    private long max;

    public long getMin() {
        return min;
    }

    public void setMin(long min) {
        this.min = min;
    }

    public long getMax() {
        return max;
    }

    public void setMax(long max) {
        this.max = max;
    }

    public boolean isFitsIntoInt() {
        return min >= Integer.MIN_VALUE && max <= Integer.MAX_VALUE;
    }

}
