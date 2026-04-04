// PushRequestDto.java
package com.thethirdlicense.controllers;

import java.util.UUID;

public class PushRequestDto {
    private UUID repositoryId;
    private int codeSize; // in KB

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(UUID repositoryId) {
        this.repositoryId = repositoryId;
    }

    public int getCodeSize() {
        return codeSize;
    }

    public void setCodeSize(int codeSize) {
        this.codeSize = codeSize;
    }
}
