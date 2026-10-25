CREATE TABLE matches (
    id bigserial,
    tour varchar(3) NOT NULL,
    tourney_id text NOT NULL,
    tourney_name text,
    surface text,
    draw_size smallint,
    tourney_level text,
    tourney_date date NOT NULL,
    round text NOT NULL,
    match_num integer NOT NULL,

    winner_id integer NOT NULL,
    winner_seed text,
    winner_entry text,
    winner_rank integer,
    winner_rank_points integer,
    winner_age numeric(4,1),
    winner_height_cm smallint,

    loser_id integer NOT NULL,
    loser_seed text,
    loser_entry text,
    loser_rank integer,
    loser_rank_points integer,
    loser_age numeric(4,1),
    loser_height_cm smallint,

    score text,
    best_of smallint,
    minutes smallint,

    w_ace smallint,
    w_df smallint,
    w_svpt smallint,
    w_1st_in smallint,
    w_1st_won smallint,
    w_2nd_won smallint,
    w_sv_gms smallint,
    w_bp_saved smallint,
    w_bp_faced smallint,

    l_ace smallint,
    l_df smallint,
    l_svpt smallint,
    l_1st_in smallint,
    l_1st_won smallint,
    l_2nd_won smallint,
    l_sv_gms smallint,
    l_bp_saved smallint,
    l_bp_faced smallint,

    CONSTRAINT pk_matches
        PRIMARY KEY (id),

    CONSTRAINT uq_matches_source
        UNIQUE (tour, tourney_id, round, match_num),

    CONSTRAINT fk_matches_winner
        FOREIGN KEY (tour, winner_id)
        REFERENCES players (tour, player_id),

    CONSTRAINT fk_matches_loser
        FOREIGN KEY (tour, loser_id)
        REFERENCES players (tour, player_id),

    CONSTRAINT ck_matches_tour
        CHECK (tour IN ('ATP', 'WTA')),

    CONSTRAINT ck_matches_tourney_id_not_blank
        CHECK (btrim(tourney_id) <> ''),

    CONSTRAINT ck_matches_round_not_blank
        CHECK (btrim(round) <> ''),

    CONSTRAINT ck_matches_winner_id_positive
        CHECK (winner_id > 0),

    CONSTRAINT ck_matches_loser_id_positive
        CHECK (loser_id > 0),

    CONSTRAINT ck_matches_draw_size_positive
        CHECK (draw_size > 0),

    CONSTRAINT ck_matches_winner_rank_positive
        CHECK (winner_rank > 0),

    CONSTRAINT ck_matches_loser_rank_positive
        CHECK (loser_rank > 0),

    CONSTRAINT ck_matches_winner_points_nonnegative
        CHECK (winner_rank_points >= 0),

    CONSTRAINT ck_matches_loser_points_nonnegative
        CHECK (loser_rank_points >= 0),

    CONSTRAINT ck_matches_winner_height_positive
        CHECK (winner_height_cm > 0),

    CONSTRAINT ck_matches_loser_height_positive
        CHECK (loser_height_cm > 0),

    CONSTRAINT ck_matches_best_of
        CHECK (best_of IN (1, 3, 5)),

    CONSTRAINT ck_matches_minutes_nonnegative
        CHECK (minutes >= 0)
);

CREATE INDEX ix_matches_winner
    ON matches (tour, winner_id, tourney_date);

CREATE INDEX ix_matches_loser
    ON matches (tour, loser_id, tourney_date);

CREATE INDEX ix_matches_h2h
    ON matches (tour, winner_id, loser_id);