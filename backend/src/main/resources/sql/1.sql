CREATE TABLE public.game_record (
  record_id    SERIAL8 PRIMARY KEY NOT NULL,
  room_id      INT   NOT NULL ,
  start_time   BIGINT NOT NULL,
  end_time     BIGINT NOT NULL,
  file_path    varchar(255) NOT NULL
  );
CREATE INDEX game_record_record_id_idx ON game_record(record_id);

CREATE TABLE public.user_in_record(
  user_id  varchar(255) NOT NULL,
  record_id BIGINT NOT NULL,
  room_id  INT NOT NULL
);
CREATE INDEX user_in_record_record_id_idx ON user_in_record(record_id);

ALTER TABLE public.user_in_record ADD nickname varchar(255) DEFAULT '' NOT NULL;

CREATE TABLE public.player_record (
  id SERIAL8 PRIMARY KEY NOT NULL,
  player_id varchar(255) NOT NULL,
  nickname varchar(255) NOT NULL,
  killing INT   NOT NULL,
  killed INT   NOT NULL,
  score FLOAT NOT NULL,
  start_time   BIGINT NOT NULL,
  end_time     BIGINT NOT NULL
);
