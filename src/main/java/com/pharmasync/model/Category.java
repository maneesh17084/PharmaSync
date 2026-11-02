package com.pharmasync.model;

public record Category(int id, String name, String description) {
    @Override
    public String toString() {
        return name;
    }
}
