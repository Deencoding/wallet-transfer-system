package com.wallettransfer.shared.money;

public enum Currency {
    NGN(2);

    private final int scale;

    Currency(int scale) {
        this.scale = scale;
    }

    public int scale() {
        return scale;
    }
}
