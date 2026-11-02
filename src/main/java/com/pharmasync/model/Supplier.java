package com.pharmasync.model;

public record Supplier(int id, String name, String contactPerson, String phone, String email, String address,
                       IndianState state, String gstin, String drugLicenseNo, boolean active) {
    @Override
    public String toString() {
        return name;
    }
}
