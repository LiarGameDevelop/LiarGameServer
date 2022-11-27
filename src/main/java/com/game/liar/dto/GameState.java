package com.game.liar.dto;

public enum GameState {
    INITIALIZE(){
        @Override
        public GameState next(){
            return BEFORE_START;
        }
    } ,
    BEFORE_START(){
        @Override
        public GameState next(){
            return IN_PROGRESS;
        }
    } ,//게임시작전
    IN_PROGRESS{
        @Override
        public GameState next(){
            return PRESENT_RESULT;
        }
    } ,//게임진행중
    PRESENT_RESULT{
        @Override
        public GameState next(){
            return END_GAME;
        }
    } , //순위선정 등 게임종료시
    END_GAME{
        @Override
        public GameState next(){
            return IN_PROGRESS;
        }
    },
    END{
        @Override
        public GameState next(){
            return END;
        }
    };

    public String getStatus(){
        return this.name();
    }
    public abstract  GameState next();
}
