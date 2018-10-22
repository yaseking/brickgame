CREATE SEQUENCE game_vedio_id_seq START WITH 100000;
CREATE TABLE public.game_video (
  video_id      BIGINT PRIMARY KEY DEFAULT nextval('game_vedio_id_seq'),
  room_id       INT   NOT NULL ,
  start_time    BIGINT NOT NULL,
  end_time      BIGINT NOT NULL
  );
CREATE INDEX game_video_video_id_idx ON game_video(video_id);

CREATE TABLE public.user_in_video(
  user_id  varchar(255) NOT NULL,
  video_id BIGINT NOT NULL,
  room_id  INT NOT NULL
)
CREATE INDEX user_in_video_video_id_idx ON user_in_video(video_id);