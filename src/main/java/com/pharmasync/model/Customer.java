package com.pharmasync.model;

public record Customer(int id, String name, CustomerType type, String contactPerson, String phone, String email,
                       String address, IndianState state, String gstin, String drugLicenseNo, boolean active) {
    @Override
    public String toString() {
        return name + " [" + type + "]";
    }
}
