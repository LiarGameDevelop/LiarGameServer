package com.game.liar.game.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.game.liar.game.dto.request.GameSettingsRequest;
import com.game.liar.game.dto.request.KeywordRequest;
import com.game.liar.game.dto.response.*;

import java.io.IOException;

/**
 * TODO: find other way to deserialize interface class
 */

//https://stackoverflow.com/questions/24263904/deserializing-polymorphic-types-with-jackson-based-on-the-presence-of-a-unique-p

public class CustomDeserializer extends StdDeserializer<MessageBase> {
    protected CustomDeserializer() {
        super(MessageBase.class);
    }

    @Override
    public MessageBase deserialize(JsonParser p, DeserializationContext context) throws IOException {
        TreeNode node = p.readValueAsTree();

        if (node.get("answer") != null) {
            return p.getCodec().treeToValue(node, LiarAnswerResponse.class);
        } else if (node.get("ownerId") != null && node.get("state") != null) {
            return p.getCodec().treeToValue(node, GameInfoResponse.class);
        } else if (node.get("matchLiar") != null) {
            return p.getCodec().treeToValue(node, OpenLiarResponse.class);
        } else if (node.get("liar") != null && node.get("state") != null) {
            return p.getCodec().treeToValue(node, LiarResponse.class);
        } else if (node.get("liar") != null) {
            return p.getCodec().treeToValue(node, LiarDesignateDto.class);
        } else if (node.get("turnOrder") != null) {
            return p.getCodec().treeToValue(node, OpenedGameInfo.class);
        } else if (node.get("category") != null && node.get("round") != null) {
            return p.getCodec().treeToValue(node, GameSettingsRequest.class);
        } else if (node.get("category") != null) {
            return p.getCodec().treeToValue(node, GameCategoryResponse.class);
        } else if (node.get("rankings") != null) {
            return p.getCodec().treeToValue(node, RankingsResponse.class);
        } else if (node.get("scoreboard") != null) {
            return p.getCodec().treeToValue(node, ScoreboardResponse.class);
        } else if (node.get("turnId") != null) {
            return p.getCodec().treeToValue(node, CurrentTurnResponse.class);
        } else if (node.get("voteResult") != null) {
            return p.getCodec().treeToValue(node, VoteResult.class);
        } else if (node.get("round") != null) {
            return p.getCodec().treeToValue(node, RoundResponse.class);
        } else if (node.get("state") != null) {
            return p.getCodec().treeToValue(node, GameStateResponse.class);
        } else if (node.get("keyword") != null) {
            return p.getCodec().treeToValue(node, KeywordRequest.class);
        } else if (node.get("errorMessage") != null) {
            return p.getCodec().treeToValue(node, ErrorResponse.class);
        }
        return null;
    }
}
