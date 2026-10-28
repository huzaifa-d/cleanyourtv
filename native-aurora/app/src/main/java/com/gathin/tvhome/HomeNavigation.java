package com.gathin.tvhome;

final class HomeNavigation {
    static final int IDLE = -2;
    static final int PROFILE = -1;
    private static final int LAST_ROW = BrandShortcut.values().length - 1;
    private static final int[] DOWN = {5, 5, 4, 6};
    private static final int[] UP = {2, 0, 3};
    private static final int[] LEFT = {0, 0, 1, 2, 5, 5, 4};
    private static final int[] RIGHT = {1, 2, 3, 3, 6, 4, 6};
    enum Direction { LEFT, RIGHT, UP, DOWN }
    private int current = IDLE;
    private int lastRow;
    int target() { return current; }
    int lastRow() { return lastRow; }
    void restore(int target, int row) {
        if (row < 0 || row > LAST_ROW) throw new IllegalArgumentException("Invalid row");
        lastRow = row;
        select(target);
    }
    int move(Direction direction) {
        if (current == IDLE) current = direction == Direction.UP ? PROFILE
                : direction == Direction.LEFT ? LAST_ROW : 0;
        else if (current == PROFILE) {
            if (direction == Direction.DOWN) current = lastRow;
        } else if (direction == Direction.UP) current = current < 4 ? PROFILE : UP[current - 4];
        else if (direction == Direction.DOWN) {
            if (current < 4) current = DOWN[current];
        } else if (direction == Direction.LEFT) current = LEFT[current];
        else if (direction == Direction.RIGHT) current = RIGHT[current];
        if (current >= 0) lastRow = current;
        return current;
    }
    void clear() { current = IDLE; }
    void select(int target) {
        if (target < IDLE || target > LAST_ROW) throw new IllegalArgumentException("Invalid target");
        current = target;
        if (target >= 0) lastRow = target;
    }
}
