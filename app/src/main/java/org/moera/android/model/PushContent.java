package org.moera.android.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PushContent {

    private PushContentType type;
    private String id;
    private StoryInfo story;

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

}
