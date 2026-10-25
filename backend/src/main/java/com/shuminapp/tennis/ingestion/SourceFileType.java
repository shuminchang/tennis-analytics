package com.shuminapp.tennis.ingestion;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public enum SourceFileType {

    ATP_PLAYERS(
            "ATP", "PLAYERS",
            "atp/atp_players\\.csv",
            playerHeader()
    ),
    WTA_PLAYERS(
            "WTA", "PLAYERS",
            "wta/wta_players\\.csv",
            playerHeader()
    ),
    ATP_MATCHES(
            "ATP", "MATCHES",
            "atp/atp_matches_[0-9]{4}\\.csv",
            matchHeader()
    ),
    WTA_MATCHES(
            "WTA", "MATCHES",
            "wta/wta_matches_[0-9]{4}\\.csv",
            matchHeader()
    ),
    ATP_RANKINGS(
            "ATP", "RANKINGS",
            "atp/atp_rankings_(70s|80s|90s|00s|10s|20s|current)\\.csv",
            List.of("ranking_date", "rank", "player", "points")
    ),
    WTA_RANKINGS(
            "WTA", "RANKINGS",
            "wta/wta_rankings_(80s|90s|00s|10s|20s|current)\\.csv",
            List.of("ranking_date", "rank", "player", "points", "tours")
    );

    private final String tour;
    private final String dataKind;
    private final Pattern pathPattern;
    private final List<String> expectedHeader;

    SourceFileType(
            String tour,
            String dataKind,
            String pathPattern,
            List<String> expectedHeader
    ) {
        this.tour = tour;
        this.dataKind = dataKind;
        this.pathPattern = Pattern.compile(pathPattern);
        this.expectedHeader = List.copyOf(expectedHeader);
    }

    public String tour() {
        return tour;
    }

    public String dataKind() {
        return dataKind;
    }

    public List<String> expectedHeader() {
        return expectedHeader;
    }

    public static SourceFileType fromPath(Path relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");

        if (relativePath.isAbsolute() || relativePath.getNameCount() != 2) {
            throw new IllegalArgumentException(
                    "Expected a source path with a tour directory and filename"
            );
        }

        String path = relativePath.getName(0)
                + "/" + relativePath.getName(1);

        for (SourceFileType type : values()) {
            if (type.pathPattern.matcher(path).matches()) {
                return type;
            }
        }

        throw new IllegalArgumentException(
                "Unsupported source file: " + relativePath
        );
    }

    private static List<String> playerHeader() {
        return List.of(
                "player_id", "name_first", "name_last", "hand",
                "dob", "ioc", "height", "wikidata_id"
        );
    }

    private static List<String> matchHeader() {
        return List.of(
                "tourney_id", "tourney_name", "surface", "draw_size",
                "tourney_level", "tourney_date", "match_num",
                "winner_id", "winner_seed", "winner_entry", "winner_name",
                "winner_hand", "winner_ht", "winner_ioc", "winner_age",
                "loser_id", "loser_seed", "loser_entry", "loser_name",
                "loser_hand", "loser_ht", "loser_ioc", "loser_age",
                "score", "best_of", "round", "minutes",
                "w_ace", "w_df", "w_svpt", "w_1stIn", "w_1stWon",
                "w_2ndWon", "w_SvGms", "w_bpSaved", "w_bpFaced",
                "l_ace", "l_df", "l_svpt", "l_1stIn", "l_1stWon",
                "l_2ndWon", "l_SvGms", "l_bpSaved", "l_bpFaced",
                "winner_rank", "winner_rank_points",
                "loser_rank", "loser_rank_points"
        );
    }
}