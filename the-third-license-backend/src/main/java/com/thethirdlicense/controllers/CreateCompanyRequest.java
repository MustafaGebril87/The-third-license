package com.thethirdlicense.controllers;

/** Request body for POST /api/companies/open — only the name is client-controlled. */
public class CreateCompanyRequest {
    private String name;

    public CreateCompanyRequest() {}

    public CreateCompanyRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
