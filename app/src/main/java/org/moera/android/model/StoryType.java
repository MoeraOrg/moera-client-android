package org.moera.android.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
    POSTING_TASK_FAILED("Operation failed"),
    COMMENT_TASK_FAILED("Operation failed"),
    POSTING_UPDATED("Post updated");

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

}
