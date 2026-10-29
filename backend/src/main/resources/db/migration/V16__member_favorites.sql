CREATE TABLE favorites (
    user_id bigint NOT NULL REFERENCES app_users(id),
    tour varchar(3) NOT NULL,
    player_id integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id,tour,player_id),
    FOREIGN KEY(tour,player_id) REFERENCES players(tour,player_id)
);
