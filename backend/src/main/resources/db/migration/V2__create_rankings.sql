CREATE TABLE rankings (
    tour varchar(3) NOT NULL,
    ranking_date date NOT NULL,
    player_id integer NOT NULL,
    rank integer NOT NULL,
    points integer,
    source_tours integer,

    CONSTRAINT pk_rankings
        PRIMARY KEY (tour, ranking_date, player_id),

    CONSTRAINT fk_rankings_player
        FOREIGN KEY (tour, player_id)
        REFERENCES players (tour, player_id),

    CONSTRAINT ck_rankings_tour
        CHECK (tour IN ('ATP', 'WTA')),

    CONSTRAINT ck_rankings_player_id_positive
        CHECK (player_id > 0),

    CONSTRAINT ck_rankings_rank_positive
        CHECK (rank > 0),

    CONSTRAINT ck_rankings_points_nonnegative
        CHECK (points >= 0),

    CONSTRAINT ck_rankings_source_tours_nonnegative
        CHECK (source_tours >= 0),

    CONSTRAINT ck_rankings_source_tours_wta_only
        CHECK (tour = 'WTA' OR source_tours IS NULL)
);

CREATE INDEX ix_rankings_player
    ON rankings (tour, player_id, ranking_date);