package org.moera.android.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PushContent {

    private PushContentType type;
    private String id;
    private StoryInfo story;
    private FeedWithStatus feedStatus;

    public PushContent() {
    }

    public PushContent(PushContentType type) {
        this.type = type;
    }

    public PushContentType getType() {
        return type;
    }

    public void setType(PushContentType type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public StoryInfo getStory() {
        return story;
    }

    public void setStory(StoryInfo story) {
        this.story = story;
    }

    public FeedWithStatus getFeedStatus() {
        return feedStatus;
    }

    public void setFeedStatus(FeedWithStatus feedStatus) {
        this.feedStatus = feedStatus;
    }

}
