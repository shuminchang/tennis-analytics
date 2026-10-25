CREATE TABLE players (
    tour varchar(3) NOT NULL,
    player_id integer NOT NULL,
    name_first text,
    name_last text,
    hand char(1),
    dob date,
    ioc varchar(3),
    height_cm smallint,
    wikidata_id text,

    CONSTRAINT pk_players PRIMARY KEY (tour, player_id),
    CONSTRAINT ck_players_tour CHECK (tour IN ('ATP', 'WTA')),
    CONSTRAINT ck_players_id_positive CHECK (player_id > 0),
    CONSTRAINT ck_players_hand CHECK (hand IN ('R', 'L', 'U', 'A')),
    CONSTRAINT ck_players_ioc CHECK (ioc ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_players_height_positive CHECK (height_cm > 0),
    CONSTRAINT ck_players_wikidata CHECK (wikidata_id ~ '^Q[1-9][0-9]*$')
);