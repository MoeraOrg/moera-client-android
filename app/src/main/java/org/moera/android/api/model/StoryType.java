package org.moera.android.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import org.moera.android.util.NodeLocation;
import org.moera.android.util.Util;

public enum StoryType {

    POSTING_ADDED("Post added"),
    REACTION_ADDED_POSITIVE("Post supported"),
    REACTION_ADDED_NEGATIVE("Post opposed"),
    MENTION_POSTING("Mention in post"),
    SUBSCRIBER_ADDED("Subscribed"),
    SUBSCRIBER_DELETED("Unsubscribed"),
    COMMENT_ADDED("Commented"),
    MENTION_COMMENT("Mention in comment"),
    REPLY_COMMENT("Reply to comment"),
    COMMENT_REACTION_ADDED_POSITIVE("Comment supported"),
    COMMENT_REACTION_ADDED_NEGATIVE("Comment opposed"),
    REMOTE_COMMENT_ADDED("Commented"),
    COMMENT_POST_TASK_FAILED("Operation failed"),
    COMMENT_UPDATE_TASK_FAILED("Operation failed"),
    POSTING_UPDATED("Post updated"),
    POSTING_POST_TASK_FAILED("Operation failed"),
    POSTING_UPDATE_TASK_FAILED("Operation failed"),
    POSTING_MEDIA_REACTION_ADDED_POSITIVE("Media in post supported"),
    POSTING_MEDIA_REACTION_ADDED_NEGATIVE("Media in post opposed"),
    COMMENT_MEDIA_REACTION_ADDED_POSITIVE("Media in comment supported"),
    COMMENT_MEDIA_REACTION_ADDED_NEGATIVE("Media in comment opposed"),
    POSTING_MEDIA_REACTION_FAILED("Operation failed"),
    COMMENT_MEDIA_REACTION_FAILED("Operation failed"),
    POSTING_SUBSCRIBE_TASK_FAILED("Operation failed"),
    POSTING_REACTION_TASK_FAILED("Operation failed"),
    COMMENT_REACTION_TASK_FAILED("Operation failed");

    StoryType(String title) {
        this.title = title;
    }

    private final String title;

    public String getTitle() {
        return title;
    }

    @JsonValue
    public String getValue() {
        return name().toLowerCase().replace('_', '-');
    }

    public static String toValue(StoryType type) {
        return type != null ? type.getValue() : null;
    }

    public static StoryType forValue(String value) {
        try {
            return parse(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @JsonCreator
    public static StoryType parse(String value) {
        return valueOf(value.toUpperCase().replace('-', '_'));
    }

    public static NodeLocation getTarget(StoryInfo storyInfo) {
        String postingId = storyInfo.getPosting() != null ? storyInfo.getPosting().getId() : null;

        switch (storyInfo.getStoryType()) {
            case REACTION_ADDED_POSITIVE:
            case REACTION_ADDED_NEGATIVE:
                return new NodeLocation(":", String.format("/post/%s", Util.ue(postingId)));

            case MENTION_POSTING:
            case COMMENT_POST_TASK_FAILED:
            case POSTING_UPDATED:
            case POSTING_UPDATE_TASK_FAILED:
            case POSTING_SUBSCRIBE_TASK_FAILED:
            case POSTING_REACTION_TASK_FAILED:
                return new NodeLocation(storyInfo.getRemoteNodeName(),
                        String.format("/post/%s", Util.ue(storyInfo.getRemotePostingId())));

            case SUBSCRIBER_ADDED:
            case SUBSCRIBER_DELETED:
            case POSTING_POST_TASK_FAILED:
                return new NodeLocation(storyInfo.getRemoteNodeName(), "/");

            case COMMENT_ADDED:
                return new NodeLocation(":",
                        String.format("/post/%s?comment=%s",
                                Util.ue(postingId), Util.ue(storyInfo.getRemoteCommentId())));

            case MENTION_COMMENT:
            case REPLY_COMMENT:
            case COMMENT_REACTION_ADDED_POSITIVE:
            case COMMENT_REACTION_ADDED_NEGATIVE:
            case COMMENT_REACTION_TASK_FAILED:
            case REMOTE_COMMENT_ADDED:
            case COMMENT_UPDATE_TASK_FAILED:
                return new NodeLocation(storyInfo.getRemoteNodeName(),
                        String.format("/post/%s?comment=%s",
                                Util.ue(storyInfo.getRemotePostingId()), Util.ue(storyInfo.getRemoteCommentId())));

            case POSTING_MEDIA_REACTION_ADDED_POSITIVE:
            case POSTING_MEDIA_REACTION_ADDED_NEGATIVE:
            case POSTING_MEDIA_REACTION_FAILED:
                return new NodeLocation(storyInfo.getRemoteNodeName(),
                        String.format("/post/%s?media=%s",
                                Util.ue(storyInfo.getRemotePostingId()), Util.ue(storyInfo.getRemoteMediaId())));

            case COMMENT_MEDIA_REACTION_ADDED_POSITIVE:
            case COMMENT_MEDIA_REACTION_ADDED_NEGATIVE:
            case COMMENT_MEDIA_REACTION_FAILED:
                return new NodeLocation(storyInfo.getRemoteNodeName(),
                        String.format("/post/%s?comment=%s&media=%s",
                                Util.ue(storyInfo.getRemotePostingId()), Util.ue(storyInfo.getRemoteCommentId()),
                                Util.ue(storyInfo.getRemoteMediaId())));

            default:
                return new NodeLocation(":", "/");
        }
    }

}
