package com.gathin.tvhome;

final class HomeNavigation {
    static final int IDLE = -2;
    static final int PROFILE = -1;
    enum Direction { LEFT, RIGHT, UP, DOWN }
    private int current = IDLE;
    private int lastRow;
    int target() { return current; }
    int lastRow() { return lastRow; }
    void restore(int target, int row) {
        if (row < 0 || row > 5) throw new IllegalArgumentException("Invalid row");
        lastRow = row;
        select(target);
    }
    int move(Direction direction) {
        if (direction == Direction.UP) current = PROFILE;
        else if (current == IDLE) current = direction == Direction.LEFT ? 5 : 0;
        else if (current == PROFILE) {
            if (direction == Direction.DOWN) current = lastRow;
        } else if (direction == Direction.LEFT) current = Math.max(0, current - 1);
        else if (direction == Direction.RIGHT) current = Math.min(5, current + 1);
        if (current >= 0) lastRow = current;
        return current;
    }
    void clear() { current = IDLE; }
    void select(int target) {
        if (target < IDLE || target > 5) throw new IllegalArgumentException("Invalid target");
        current = target;
        if (target >= 0) lastRow = target;
    }
}
