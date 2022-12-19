package com.game.liar.domain;

public enum GameState {
    INITIAL() {
        @Override
        public GameState next() {
            return BEFORE_START;
        }
        @Override
        public GameState loop() {
            return BEFORE_START;
        }
    },
    BEFORE_START() {
        @Override
        public GameState next() {
            return BEFORE_ROUND;
        }
        @Override
        public GameState loop() {
            return BEFORE_ROUND;
        }
    },
    BEFORE_ROUND() {
        @Override
        public GameState next() {
            return SELECT_LIAR;
        }
        @Override
        public GameState loop() {
            return SELECT_LIAR;
        }
    },
    SELECT_LIAR() {
        @Override
        public GameState next() {
            return OPEN_KEYWORD;
        }
        @Override
        public GameState loop() {
            return OPEN_KEYWORD;
        }
    },
    OPEN_KEYWORD() {
        @Override
        public GameState next() {
            return IN_PROGRESS;
        }
        @Override
        public GameState loop() {
            return IN_PROGRESS;
        }
    },
    IN_PROGRESS() {
        @Override
        public GameState next() {
            return VOTE_LIAR;
        }
        @Override
        public GameState loop() {
            return VOTE_LIAR;
        }
    },
    VOTE_LIAR() {
        @Override
        public GameState next() {
            return OPEN_LIAR;
        }
        @Override
        public GameState loop() {
            return VOTE_LIAR;
        }
    },
    OPEN_LIAR() {
        @Override
        public GameState next() {
            return LIAR_ANSWER;
        }
        @Override
        public GameState loop() {
            return LIAR_ANSWER;
        }
    },
    LIAR_ANSWER() {
        @Override
        public GameState next() {
            return PUBLISH_SCORE;
        }
        @Override
        public GameState loop() {
            return PUBLISH_SCORE;
        }
    },
    PUBLISH_SCORE() {
        @Override
        public GameState next() {
            return END_ROUND;
        }
        @Override
        public GameState loop() {
            return END_ROUND;
        }
    },
    END_ROUND() {
        @Override
        public GameState next() {
            return PUBLISH_RANKINGS;
        }
        @Override
        public GameState loop() {
            return PUBLISH_RANKINGS;
        }
    },
    PUBLISH_RANKINGS() {
        @Override
        public GameState next() {
            return END_GAME;
        }
        @Override
        public GameState loop() {
            return END_GAME;
        }
    },//순위선정 등 게임종료시
    END_GAME() {
        @Override
        public GameState next() {
            return END;
        }
        @Override
        public GameState loop() {
            return END;
        }
    },
    END() {
        @Override
        public GameState next() {
            return INITIAL;
        }
        @Override
        public GameState loop() {
            return INITIAL;
        }
    };

    public String getStatus() {
        return this.name();
    }

    public abstract GameState next();
    public abstract GameState loop();
}
