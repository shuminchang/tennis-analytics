package com.shuminapp.tennis.ingestion;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

public record SourceSnapshotIdentity(
        URI repositoryUrl,
        String commitSha
) {

    private static final Pattern COMMIT_SHA =
            Pattern.compile("[0-9a-f]{40}");

    public SourceSnapshotIdentity {
        Objects.requireNonNull(repositoryUrl, "repositoryUrl");
        Objects.requireNonNull(commitSha, "commitSha");

        String path = repositoryUrl.getPath();

        if (!"https".equalsIgnoreCase(repositoryUrl.getScheme())
                || repositoryUrl.getHost() == null
                || path == null
                || path.isBlank()
                || path.equals("/")
                || repositoryUrl.getRawUserInfo() != null
                || repositoryUrl.getRawQuery() != null
                || repositoryUrl.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "Repository URL must be HTTPS with a host and repository path,"
                            + " without user info, query or fragment"
            );
        }

        if (!COMMIT_SHA.matcher(commitSha).matches()) {
            throw new IllegalArgumentException(
                    "Commit SHA must contain exactly 40 lowercase hexadecimal characters"
            );
        }
    }
}